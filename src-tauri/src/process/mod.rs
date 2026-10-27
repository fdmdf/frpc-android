use crate::models::{ApiResponse, FrpcProcessStatus};
use std::fs::OpenOptions;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, AtomicU32, Ordering};
use std::sync::Arc;
use std::time::Duration;
use std::time::{SystemTime, UNIX_EPOCH};
use tauri::{AppHandle, Emitter};
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::process::Command;
use tokio::sync::{mpsc, Mutex};

/// 进程意外退出后的自动重启策略，与 frpc-android 的 FrpcSupervisor 保持一致。
const MAX_RESTARTS: u32 = 5;
const RESTART_BACKOFF_MS: [u64; 5] = [2_000, 5_000, 10_000, 30_000, 60_000];

/// 一次待执行的重启：由看护任务投递，监督循环消费。
struct RestartTicket {
    binary_path: PathBuf,
    config_path: PathBuf,
    log_path: PathBuf,
    delay_ms: u64,
    sender: mpsc::Sender<RestartTicket>,
}

#[derive(Clone)]
pub struct ProcessManager {
    inner: Arc<Mutex<ProcessState>>,
    is_running_flag: Arc<AtomicBool>,
    /// 用户主动停止，避免把「stop」误判成崩溃而自动重启
    stopping: Arc<AtomicBool>,
    restart_count: Arc<AtomicU32>,
}

struct ProcessState {
    child_pid: Option<u32>,
    last_start_time: i64,
    connection_error: Option<String>,
    log_path: PathBuf,
}

impl ProcessManager {
    pub fn new() -> Self {
        Self {
            inner: Arc::new(Mutex::new(ProcessState {
                child_pid: None,
                last_start_time: -1,
                connection_error: None,
                log_path: PathBuf::new(),
            })),
            is_running_flag: Arc::new(AtomicBool::new(false)),
            stopping: Arc::new(AtomicBool::new(false)),
            restart_count: Arc::new(AtomicU32::new(0)),
        }
    }

    pub async fn is_running(&self) -> bool {
        self.is_running_flag.load(Ordering::SeqCst)
    }

    pub async fn get_status(&self) -> FrpcProcessStatus {
        let state = self.inner.lock().await;
        FrpcProcessStatus {
            running: self.is_running_flag.load(Ordering::SeqCst),
            connection_error: state.connection_error.clone(),
            last_start_time: if state.last_start_time > 0 {
                Some(state.last_start_time)
            } else {
                None
            },
        }
    }

    pub async fn start(
        &self,
        app: AppHandle,
        binary_path: &Path,
        config_path: &Path,
        log_path: &Path,
    ) -> anyhow::Result<()> {
        if self.is_running().await {
            return Ok(());
        }

        self.stopping.store(false, Ordering::SeqCst);
        self.restart_count.store(0, Ordering::SeqCst);

        // 重启交给独立的监督循环：看护任务只投递票据，不在自己的 future 里等待
        // spawn_child（否则会形成递归 future，rustc 无法证明其 Send）。
        let (tx, mut rx) = mpsc::channel::<RestartTicket>(1);
        {
            let supervisor = self.clone();
            let app_handle = app.clone();
            tauri::async_runtime::spawn(async move {
                while let Some(ticket) = rx.recv().await {
                    tokio::time::sleep(Duration::from_millis(ticket.delay_ms)).await;
                    if supervisor.stopping.load(Ordering::SeqCst) {
                        continue;
                    }
                    let sender = ticket.sender.clone();
                    if let Err(e) = supervisor
                        .spawn_child(
                            app_handle.clone(),
                            ticket.binary_path,
                            ticket.config_path,
                            ticket.log_path,
                            sender,
                        )
                        .await
                    {
                        log::error!("auto restart failed: {}", e);
                        let mut state = supervisor.inner.lock().await;
                        state.connection_error = Some(format!("自动重连失败: {}", e));
                        let status = FrpcProcessStatus {
                            running: false,
                            connection_error: state.connection_error.clone(),
                            last_start_time: None,
                        };
                        drop(state);
                        let _ =
                            app_handle.emit("frpcProcess:watchFrpcLog", ApiResponse::success(status));
                    }
                }
            });
        }

        self.spawn_child(
            app,
            binary_path.to_path_buf(),
            config_path.to_path_buf(),
            log_path.to_path_buf(),
            tx,
        )
        .await
    }

    async fn spawn_child(
        &self,
        app: AppHandle,
        binary_path: PathBuf,
        config_path: PathBuf,
        log_path: PathBuf,
        restart_sender: mpsc::Sender<RestartTicket>,
    ) -> anyhow::Result<()> {
        let binary = binary_path.as_path();
        let config = config_path.as_path();

        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)?
            .as_millis() as i64;

        {
            let mut state = self.inner.lock().await;
            state.connection_error = None;
            state.last_start_time = now;
            state.log_path = log_path.clone();
        }

        // Android 上 frpc 以 libfrpc.so 形式随 APK 安装，执行前兜底修一次权限，
        // 并拉起前台服务，避免切后台后进程被系统冻结。
        #[cfg(target_os = "android")]
        {
            use std::os::unix::fs::PermissionsExt;
            let _ = std::fs::set_permissions(binary, std::fs::Permissions::from_mode(0o755));
            crate::android::start_service(&app);
        }

        let mut cmd = Command::new(binary);
        cmd.arg("-c").arg(config);
        cmd.stdout(std::process::Stdio::piped());
        cmd.stderr(std::process::Stdio::piped());

        #[cfg(target_os = "windows")]
        {
            const CREATE_NO_WINDOW: u32 = 0x08000000;
            cmd.creation_flags(CREATE_NO_WINDOW);
        }

        let mut child = cmd.spawn()?;
        let pid = child.id().unwrap_or(0);

        {
            let mut state = self.inner.lock().await;
            state.child_pid = Some(pid);
        }
        self.is_running_flag.store(true, Ordering::SeqCst);

        #[cfg(target_os = "android")]
        crate::android::update_notification(&app, true);

        let initial_status = FrpcProcessStatus {
            running: true,
            connection_error: None,
            last_start_time: Some(now),
        };
        let _ = app.emit(
            "frpcProcess:watchFrpcLog",
            ApiResponse::success(initial_status),
        );

        let stdout = child.stdout.take();
        let stderr = child.stderr.take();

        let inner_clone = self.inner.clone();
        let running_flag_clone = self.is_running_flag.clone();
        let app_clone = app.clone();
        let stopping_clone = self.stopping.clone();
        let restart_count_clone = self.restart_count.clone();

        // Spawn background task to monitor child exit and logs
        tauri::async_runtime::spawn(async move {
            let log_file_path = log_path.clone();
            let log_file_path_stdout = log_file_path.clone();
            let app_out = app_clone.clone();
            let inner_out = inner_clone.clone();

            if let Some(stdout) = stdout {
                tauri::async_runtime::spawn(async move {
                    let mut reader = BufReader::new(stdout).lines();
                    while let Ok(Some(line)) = reader.next_line().await {
                        Self::write_log_line(&log_file_path_stdout, &line);
                        Self::inspect_log_line(&inner_out, &app_out, &line).await;
                    }
                });
            }

            let log_file_path_stderr = log_file_path.clone();
            let app_err = app_clone.clone();
            let inner_err = inner_clone.clone();

            if let Some(stderr) = stderr {
                tauri::async_runtime::spawn(async move {
                    let mut reader = BufReader::new(stderr).lines();
                    while let Ok(Some(line)) = reader.next_line().await {
                        Self::write_log_line(&log_file_path_stderr, &line);
                        Self::inspect_log_line(&inner_err, &app_err, &line).await;
                    }
                });
            }

            let status = child.wait().await;
            log::info!("frpc process exited: {:?}", status);

            let is_err_exit = match &status {
                Ok(s) => !s.success(),
                Err(_) => true,
            };

            running_flag_clone.store(false, Ordering::SeqCst);
            let conn_err = {
                let mut state = inner_clone.lock().await;
                state.child_pid = None;
                state.last_start_time = -1;
                if is_err_exit && state.connection_error.is_none() {
                    state.connection_error = Some("frpc 异常退出，请查看日志".to_string());
                }
                state.connection_error.clone()
            };

            // 用户主动停止不算异常，不做重启。
            if stopping_clone.load(Ordering::SeqCst) {
                #[cfg(target_os = "android")]
                crate::android::stop_service(&app_clone);
                let final_status = FrpcProcessStatus {
                    running: false,
                    connection_error: conn_err,
                    last_start_time: None,
                };
                let _ =
                    app_clone.emit("frpcProcess:watchFrpcLog", ApiResponse::success(final_status));
                return;
            }

            let attempt = restart_count_clone.fetch_add(1, Ordering::SeqCst) + 1;
            if attempt > MAX_RESTARTS {
                log::error!("frpc exited {} times, giving up auto restart", attempt);
                let mut state = inner_clone.lock().await;
                state.connection_error = Some(format!(
                    "frpc 连续 {} 次异常退出，已停止自动重启，请查看日志",
                    MAX_RESTARTS
                ));
                let final_status = FrpcProcessStatus {
                    running: false,
                    connection_error: state.connection_error.clone(),
                    last_start_time: None,
                };
                drop(state);
                #[cfg(target_os = "android")]
                crate::android::stop_service(&app_clone);
                let _ =
                    app_clone.emit("frpcProcess:watchFrpcLog", ApiResponse::success(final_status));
                return;
            }

            let backoff =
                RESTART_BACKOFF_MS[(attempt as usize - 1).min(RESTART_BACKOFF_MS.len() - 1)];
            log::warn!(
                "frpc exited unexpectedly, restart #{}/{} in {}ms",
                attempt,
                MAX_RESTARTS,
                backoff
            );

            // 先告诉界面「正在重连」，避免看起来像彻底断开。
            {
                let state = inner_clone.lock().await;
                let status = FrpcProcessStatus {
                    running: false,
                    connection_error: Some(format!(
                        "frpc 意外退出，{} 秒后自动重连（第 {}/{} 次）",
                        backoff / 1000,
                        attempt,
                        MAX_RESTARTS
                    )),
                    last_start_time: if state.last_start_time > 0 {
                        Some(state.last_start_time)
                    } else {
                        None
                    },
                };
                drop(state);
                let _ = app_clone.emit("frpcProcess:watchFrpcLog", ApiResponse::success(status));
            }

            // 投递给监督循环，自身不再等待重启，避免递归 future。
            let ticket = RestartTicket {
                binary_path,
                config_path,
                log_path: log_file_path,
                delay_ms: backoff,
                sender: restart_sender.clone(),
            };
            if restart_sender.send(ticket).await.is_err() {
                log::error!("restart supervisor is gone");
            }
        });

        Ok(())
    }

    pub async fn terminate(&self, app: AppHandle) -> anyhow::Result<()> {
        // 标记主动停止，阻止看护任务自动重启。
        self.stopping.store(true, Ordering::SeqCst);
        self.restart_count.store(0, Ordering::SeqCst);

        let pid = {
            let state = self.inner.lock().await;
            state.child_pid
        };

        if let Some(pid) = pid {
            Self::kill_process_tree(pid);
        }

        self.is_running_flag.store(false, Ordering::SeqCst);
        {
            let mut state = self.inner.lock().await;
            state.child_pid = None;
            state.last_start_time = -1;
            state.connection_error = None;
        }

        #[cfg(target_os = "android")]
        crate::android::stop_service(&app);

        let final_status = FrpcProcessStatus {
            running: false,
            connection_error: None,
            last_start_time: None,
        };
        let _ = app.emit("frpcProcess:watchFrpcLog", ApiResponse::success(final_status));

        Ok(())
    }

    fn write_log_line(path: &Path, line: &str) {
        if let Ok(mut f) = OpenOptions::new().create(true).append(true).open(path) {
            let _ = writeln!(f, "{}", line);
        }
    }

    async fn inspect_log_line(inner: &Arc<Mutex<ProcessState>>, app: &AppHandle, line: &str) {
        let is_error = line.contains("connect to server error")
            || line.contains("login to server failed")
            || line.contains("address already in use")
            || line.contains("bind: address already in use")
            || line.contains("port already in use");
        let is_success = line.contains("login to server success")
            || line.contains("start proxy success")
            || line.contains("proxy added success")
            || line.contains("proxy added:");

        if is_error {
            let mut state = inner.lock().await;
            state.connection_error = Some(line.to_string());
            let status = FrpcProcessStatus {
                running: true,
                connection_error: Some(line.to_string()),
                last_start_time: if state.last_start_time > 0 {
                    Some(state.last_start_time)
                } else {
                    None
                },
            };
            drop(state);
            let _ = app.emit("frpcProcess:watchFrpcLog", ApiResponse::success(status));
        } else if is_success {
            let mut state = inner.lock().await;
            if state.connection_error.is_some() {
                state.connection_error = None;
                let status = FrpcProcessStatus {
                    running: true,
                    connection_error: None,
                    last_start_time: if state.last_start_time > 0 {
                        Some(state.last_start_time)
                    } else {
                        None
                    },
                };
                drop(state);
                let _ = app.emit("frpcProcess:watchFrpcLog", ApiResponse::success(status));
            }
        }
    }

    fn kill_process_tree(pid: u32) {
        #[cfg(target_os = "windows")]
        {
            let _ = std::process::Command::new("taskkill")
                .args(["/F", "/T", "/PID", &pid.to_string()])
                .output();
        }

        #[cfg(not(target_os = "windows"))]
        {
            unsafe {
                libc::kill(pid as i32, libc::SIGTERM);
                std::thread::sleep(std::time::Duration::from_millis(200));
                libc::kill(pid as i32, libc::SIGKILL);
            }
        }
    }
}
