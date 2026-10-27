use crate::config_gen::ConfigGenerator;
use crate::db::app_config_repo::AppConfigRepository;
use crate::db::proxy_repo::ProxyRepository;
use crate::db::server_repo::ServerRepository;
use crate::db::DbManager;
use crate::models::{ApiResponse, FrpcProcessStatus, FrpcProxy, FrpcServerConfig};
use crate::process::ProcessManager;
use crate::system::SystemService;
use serde_json::Value;
use std::fs;
use std::path::PathBuf;
use tauri::{AppHandle, State};

pub struct AppState {
    pub db: DbManager,
    pub process_mgr: ProcessManager,
    pub config_path: PathBuf,
    pub frpc_log_path: PathBuf,
    pub app_log_path: PathBuf,
}

/// 内置 frpc 路径：随 APK 安装到 nativeLibraryDir 的 `libfrpc.so`。
#[cfg(target_os = "android")]
fn bundled_frpc_binary(app: &AppHandle) -> Result<PathBuf, (String, String)> {
    crate::android::frpc_binary(app).ok_or_else(|| {
        (
            "B1005".to_string(),
            "内置 frpc 缺失，请重新安装应用".to_string(),
        )
    })
}

/// 本项目只面向 Android，其他目标仅用于本地 `cargo check`/单测，无法实际运行 frpc。
#[cfg(not(target_os = "android"))]
fn bundled_frpc_binary(_app: &AppHandle) -> Result<PathBuf, (String, String)> {
    Err((
        "B1005".to_string(),
        "内置 frpc 仅在 Android 目标可用".to_string(),
    ))
}

impl AppState {
    /// 启动 frpc。
    ///
    /// Android 上的 frpc 随 APK 分发（`libfrpc.so` 装在 nativeLibraryDir），
    /// 既不能在线下载也不能改路径，因此固定使用内置二进制。
    pub async fn launch_frpc(&self, app: &AppHandle) -> Result<FrpcProcessStatus, (String, String)> {
        let (server, proxies) = {
            let conn = self.db.conn();
            let server = match ServerRepository::get_server_config(&conn) {
                Ok(s) => s,
                Err(e) => return Err(("B1000".to_string(), e.to_string())),
            };

            if server.server_addr.trim().is_empty() {
                return Err(("B1001".to_string(), "未配置服务端地址".to_string()));
            }

            if server.web_server.enable && server.web_server.port > 0 {
                let bind_addr = format!("127.0.0.1:{}", server.web_server.port);
                if std::net::TcpListener::bind(&bind_addr).is_err() {
                    return Err(("B1006".to_string(), "WebServer Port In Use".to_string()));
                }
            }

            let proxies = match ProxyRepository::get_all_proxies(&conn) {
                Ok(p) => p,
                Err(e) => return Err(("B1000".to_string(), e.to_string())),
            };
            (server, proxies)
        };

        let binary_path = bundled_frpc_binary(app)?;

        let toml_str = ConfigGenerator::gen_toml_config(&server, &proxies, &self.frpc_log_path);
        if let Err(e) = fs::write(&self.config_path, toml_str) {
            return Err(("B1000".to_string(), format!("写入配置文件失败: {}", e)));
        }

        match self
            .process_mgr
            .start(
                app.clone(),
                &binary_path,
                &self.config_path,
                &self.frpc_log_path,
            )
            .await
        {
            Ok(()) => {
                let status = self.process_mgr.get_status().await;
                Ok(status)
            }
            Err(e) => Err(("B1000".to_string(), e.to_string())),
        }
    }
}

#[tauri::command]
pub async fn ipc_send(
    app: AppHandle,
    state: State<'_, AppState>,
    path: String,
    args: Option<Value>,
) -> Result<ApiResponse<Value>, String> {
    let result = handle_ipc_send(&app, &state, &path, args).await;
    Ok(result)
}

async fn handle_ipc_send(
    app: &AppHandle,
    state: &AppState,
    path: &str,
    args: Option<Value>,
) -> ApiResponse<Value> {
    match path {
        // --- SERVER ---
        "server/getServerConfig" => {
            let conn = state.db.conn();
            match ServerRepository::get_server_config(&conn) {
                Ok(cfg) => ApiResponse::success(serde_json::to_value(cfg).unwrap_or(Value::Null)),
                Err(e) => ApiResponse::internal_error(e.to_string()),
            }
        }
        "server/saveConfig" => {
            if let Some(val) = args {
                match serde_json::from_value::<FrpcServerConfig>(val) {
                    Ok(mut server) => {
                        server.id = "1".to_string();
                        let conn = state.db.conn();
                        match ServerRepository::save_server_config(&conn, &server) {
                            Ok(()) => {
                                ApiResponse::success(serde_json::to_value(&server).unwrap_or(Value::Null))
                            }
                            Err(e) => ApiResponse::internal_error(e.to_string()),
                        }
                    }
                    Err(e) => ApiResponse::internal_error(format!("Invalid server payload: {}", e)),
                }
            } else {
                ApiResponse::internal_error("Missing arguments for saveConfig")
            }
        }
        "server/resetAllConfig" => {
            let conn = state.db.conn();
            match ServerRepository::reset_all_config(&conn) {
                Ok(()) => {
                    let default_server = ServerRepository::get_server_config(&conn).unwrap();
                    ApiResponse::success(serde_json::to_value(default_server).unwrap_or(Value::Null))
                }
                Err(e) => ApiResponse::internal_error(e.to_string()),
            }
        }
        "server/getLanguage" => {
            let conn = state.db.conn();
            match AppConfigRepository::get_system_config(&conn) {
                Ok(sys) => ApiResponse::success(Value::String(sys.language)),
                Err(e) => ApiResponse::internal_error(e.to_string()),
            }
        }
        "server/saveLanguage" => {
            let lang = match args {
                Some(Value::String(s)) => s,
                Some(Value::Object(map)) => map
                    .get("language")
                    .and_then(|v| v.as_str())
                    .unwrap_or("en-US")
                    .to_string(),
                _ => "en-US".to_string(),
            };
            let conn = state.db.conn();
            match AppConfigRepository::save_language(&conn, &lang) {
                Ok(()) => ApiResponse::success_empty(),
                Err(e) => ApiResponse::internal_error(e.to_string()),
            }
        }

        // --- PROXY ---
        "proxy/getAllProxies" => {
            let conn = state.db.conn();
            match ProxyRepository::get_all_proxies(&conn) {
                Ok(list) => ApiResponse::success(serde_json::to_value(list).unwrap_or(Value::Null)),
                Err(e) => ApiResponse::internal_error(e.to_string()),
            }
        }
        "proxy/createProxy" => {
            if let Some(val) = args {
                match serde_json::from_value::<FrpcProxy>(val) {
                    Ok(proxy) => {
                        let conn = state.db.conn();
                        match ProxyRepository::create_proxy(&conn, proxy) {
                            Ok(created) => {
                                ApiResponse::success(serde_json::to_value(created).unwrap_or(Value::Null))
                            }
                            Err(e) => ApiResponse::internal_error(e.to_string()),
                        }
                    }
                    Err(e) => ApiResponse::internal_error(format!("Invalid proxy payload: {}", e)),
                }
            } else {
                ApiResponse::internal_error("Missing arguments for createProxy")
            }
        }
        "proxy/modifyProxy" => {
            if let Some(val) = args {
                match serde_json::from_value::<FrpcProxy>(val) {
                    Ok(proxy) => {
                        let conn = state.db.conn();
                        match ProxyRepository::modify_proxy(&conn, proxy) {
                            Ok(updated) => {
                                ApiResponse::success(serde_json::to_value(updated).unwrap_or(Value::Null))
                            }
                            Err(e) => ApiResponse::internal_error(e.to_string()),
                        }
                    }
                    Err(e) => ApiResponse::internal_error(format!("Invalid proxy payload: {}", e)),
                }
            } else {
                ApiResponse::internal_error("Missing arguments for modifyProxy")
            }
        }
        "proxy/deleteProxy" => {
            let proxy_id = match args {
                Some(Value::String(s)) => s,
                Some(Value::Object(map)) => map
                    .get("id")
                    .or_else(|| map.get("_id"))
                    .and_then(|v| v.as_str())
                    .unwrap_or("")
                    .to_string(),
                _ => String::new(),
            };
            if proxy_id.is_empty() {
                return ApiResponse::internal_error("Missing proxy id for deleteProxy");
            }
            let conn = state.db.conn();
            match ProxyRepository::delete_proxy(&conn, &proxy_id) {
                Ok(()) => ApiResponse::success_empty(),
                Err(e) => ApiResponse::internal_error(e.to_string()),
            }
        }
        "proxy/modifyProxyStatus" => {
            if let Some(Value::Object(map)) = args {
                let id = map
                    .get("id")
                    .or_else(|| map.get("_id"))
                    .and_then(|v| v.as_str())
                    .unwrap_or("");
                let status = map.get("status").and_then(|v| v.as_i64()).unwrap_or(1);
                let conn = state.db.conn();
                match ProxyRepository::modify_proxy_status(&conn, id, status) {
                    Ok(()) => ApiResponse::success_empty(),
                    Err(e) => ApiResponse::internal_error(e.to_string()),
                }
            } else {
                ApiResponse::internal_error("Invalid payload for modifyProxyStatus")
            }
        }
        "proxy/getLocalPorts" => {
            let conn = state.db.conn();
            match ProxyRepository::get_local_ports(&conn) {
                Ok(ports) => ApiResponse::success(serde_json::to_value(ports).unwrap_or(Value::Null)),
                Err(e) => ApiResponse::internal_error(e.to_string()),
            }
        }

        // --- LAUNCH ---
        "launch/getStatus" => {
            let status = state.process_mgr.get_status().await;
            ApiResponse::success(serde_json::to_value(status).unwrap_or(Value::Null))
        }
        "launch/terminate" => match state.process_mgr.terminate(app.clone()).await {
            Ok(()) => ApiResponse::success_empty(),
            Err(e) => ApiResponse::internal_error(e.to_string()),
        },
        "launch/launch" => match state.launch_frpc(app).await {
            Ok(status) => ApiResponse::success(serde_json::to_value(status).unwrap_or(Value::Null)),
            Err((code, msg)) => ApiResponse::fail(code, msg),
        },

        // --- LOG ---
        "log/getFrpLogContent" => {
            let lines = SystemService::read_log_tail(&state.frpc_log_path, 500);
            ApiResponse::success(Value::String(lines.join("\n")))
        }
        "log/getAppLogContent" => {
            let lines = SystemService::read_log_tail(&state.app_log_path, 500);
            ApiResponse::success(Value::String(lines.join("\n")))
        }

        // --- SYSTEM ---
        "system/openUrl" => {
            if let Some(Value::Object(map)) = args {
                if let Some(url) = map.get("url").and_then(|v| v.as_str()) {
                    #[cfg(target_os = "android")]
                    let _ = SystemService::open_url(app, url);
                    #[cfg(not(target_os = "android"))]
                    {
                        let _ = url;
                        log::warn!("openUrl is only implemented on Android");
                    }
                }
            }
            ApiResponse::success_empty()
        }
        "system/relaunchApp" => {
            app.restart();
        }
        "system/getGuideStatus" => {
            let conn = state.db.conn();
            match AppConfigRepository::is_guide_completed(&conn) {
                Ok(completed) => ApiResponse::success(Value::Bool(completed)),
                Err(e) => ApiResponse::internal_error(e.to_string()),
            }
        }
        "system/completeGuide" => {
            let conn = state.db.conn();
            match AppConfigRepository::save_guide_completed(&conn) {
                Ok(()) => ApiResponse::success_empty(),
                Err(e) => ApiResponse::internal_error(e.to_string()),
            }
        }
        "system/getGithubLastRelease" => {
            let manual = args
                .as_ref()
                .and_then(|v| v.get("manual"))
                .and_then(|m| m.as_bool())
                .unwrap_or(false);

            if let Some(release) = SystemService::check_latest_release().await {
                ApiResponse::success(serde_json::json!({
                    "manual": manual,
                    "version": release
                }))
            } else {
                ApiResponse::success(serde_json::json!({
                    "manual": manual,
                    "version": serde_json::Value::Null
                }))
            }
        }

        unknown => {
            log::warn!("Unhandled IPC path: {}", unknown);
            ApiResponse::internal_error(format!("Unhandled path: {}", unknown))
        }
    }
}
