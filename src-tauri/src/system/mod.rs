pub mod detector;
pub use detector::{DetectFrpcResult, FrpcDetector};

#[cfg(desktop)]
use crate::models::ApiResponse;
use std::fs::File;
use std::io::{BufRead, BufReader};
use std::path::Path;
use std::time::Duration;

#[cfg(desktop)]
use crate::models::SystemUsage;
#[cfg(desktop)]
use crate::models::SystemMemoryUsage;
#[cfg(desktop)]
use std::sync::Arc;
#[cfg(desktop)]
use sysinfo::System;
#[cfg(desktop)]
use tauri::{AppHandle, Emitter};
#[cfg(desktop)]
use tokio::sync::Mutex;

pub struct SystemService;

impl SystemService {
    #[cfg(desktop)]
    pub fn start_usage_poller(app: AppHandle) {
        tauri::async_runtime::spawn(async move {
            let sys = Arc::new(Mutex::new(System::new_all()));
            loop {
                tokio::time::sleep(Duration::from_secs(3)).await;
                let mut system = sys.lock().await;
                system.refresh_cpu_usage();
                system.refresh_memory();

                let cpu = system.global_cpu_usage();
                let total_mem = system.total_memory();
                let used_mem = system.used_memory();
                let percentage = if total_mem > 0 {
                    (used_mem as f32 / total_mem as f32) * 100.0
                } else {
                    0.0
                };

                let usage = SystemUsage {
                    cpu,
                    memory: SystemMemoryUsage {
                        used: used_mem / (1024 * 1024), // MB
                        percentage,
                    },
                };

                let _ = app.emit("system:watchSystemUsage", ApiResponse::success(usage));
            }
        });
    }

    /// 移动端没有系统占用轮询，保留空实现避免调用点到处 cfg。
    #[cfg(mobile)]
    pub fn start_usage_poller(_app: tauri::AppHandle) {}

    pub fn open_url(url: &str) -> anyhow::Result<()> {
        #[cfg(desktop)]
        {
            open::that(url)?;
            Ok(())
        }
        #[cfg(mobile)]
        {
            let _ = url;
            Ok(())
        }
    }

    /// Android 上用系统浏览器打开链接，需要 AppHandle 才能走原生插件。
    #[cfg(target_os = "android")]
    pub fn open_url_with_app(app: &tauri::AppHandle, url: &str) -> anyhow::Result<()> {
        if crate::android::open_url(app, url) {
            Ok(())
        } else {
            anyhow::bail!("open url failed")
        }
    }

    pub fn open_path<P: AsRef<Path>>(path: P) -> anyhow::Result<()> {
        #[cfg(desktop)]
        {
            open::that(path.as_ref())?;
            Ok(())
        }
        #[cfg(mobile)]
        {
            // 移动端没有「文件管理器打开目录」的概念，静默忽略。
            let _ = path;
            Ok(())
        }
    }

    pub fn read_log_tail<P: AsRef<Path>>(path: P, max_lines: usize) -> Vec<String> {
        let path = path.as_ref();
        if !path.exists() {
            return Vec::new();
        }

        if let Ok(file) = File::open(path) {
            let reader = BufReader::new(file);
            let lines: Vec<String> = reader.lines().filter_map(|l| l.ok()).collect();
            if lines.len() > max_lines {
                lines[lines.len() - max_lines..].to_vec()
            } else {
                lines
            }
        } else {
            Vec::new()
        }
    }

    pub async fn check_latest_release() -> Option<serde_json::Value> {
        let client = reqwest::Client::builder()
            .user_agent(concat!("frpc-desktop/", env!("CARGO_PKG_VERSION")))
            .timeout(Duration::from_secs(5))
            .build()
            .ok()?;

        let urls = [
            "https://gh.jwinks.com/api/repos/luckjiawei/frpc-desktop/releases/latest",
            "https://api.github.com/repos/luckjiawei/frpc-desktop/releases/latest",
        ];

        for url in urls {
            if let Ok(resp) = client.get(url).send().await {
                if resp.status().is_success() {
                    if let Ok(val) = resp.json::<serde_json::Value>().await {
                        return Some(val);
                    }
                }
            }
        }
        None
    }
}
