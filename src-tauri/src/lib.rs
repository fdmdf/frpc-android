pub mod commands;
pub mod config_gen;
pub mod db;
pub mod models;
pub mod process;
pub mod system;

#[cfg(target_os = "android")]
pub mod android;

use commands::{ipc_send, AppState};
use db::DbManager;
use process::ProcessManager;
use std::fs;
use std::path::PathBuf;
use tauri::Manager;

/// Android 使用应用私有目录，SQLite、配置与日志都放在这里。
fn resolve_base_dir(app: &tauri::AppHandle) -> PathBuf {
    app.path()
        .app_data_dir()
        .unwrap_or_else(|_| PathBuf::from("."))
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    #[allow(unused_mut)]
    let mut builder = tauri::Builder::default();

    // 前台服务保活与内置 frpc 路径都靠这个插件桥接到 Kotlin 侧。
    #[cfg(target_os = "android")]
    let builder = builder.plugin(android::init());

    builder
        .setup(|app| {
            let base_dir = resolve_base_dir(app.handle());
            let _ = fs::create_dir_all(&base_dir);

            let db_dir = base_dir.join("db");
            let config_dir = base_dir.join("config");
            let log_dir = base_dir.join("log");
            let _ = fs::create_dir_all(&config_dir);
            let _ = fs::create_dir_all(&log_dir);

            let db = DbManager::init(&db_dir)?;
            let process_mgr = ProcessManager::new();

            let app_state = AppState {
                db,
                process_mgr,
                config_path: config_dir.join("frpc.toml"),
                frpc_log_path: log_dir.join("frpc.log"),
                app_log_path: log_dir.join("main.log"),
            };

            app.manage(app_state);

            // 移动端主窗口就是 Activity，必须显式显示，否则会看到一个空白页面。
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.show();
            }

            let server_cfg = {
                let state = app.state::<AppState>();
                let conn = state.db.conn();
                db::server_repo::ServerRepository::get_server_config(&conn).ok()
            };

            if let Some(server) = server_cfg {
                if server.system.auto_connect_on_startup {
                    let app_handle = app.handle().clone();
                    tauri::async_runtime::spawn(async move {
                        tokio::time::sleep(std::time::Duration::from_millis(800)).await;
                        let state = app_handle.state::<AppState>();
                        if let Err((code, msg)) = state.launch_frpc(&app_handle).await {
                            eprintln!("Auto connect on startup failed: [{}]: {}", code, msg);
                        }
                    });
                }
            }

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![ipc_send])
        .build(tauri::generate_context!())
        .expect("error while running tauri application")
        .run(|app_handle, event| {
            if let tauri::RunEvent::ExitRequested { .. } | tauri::RunEvent::Exit = event {
                let state = app_handle.state::<AppState>();
                let p_mgr = state.process_mgr.clone();
                let app = app_handle.clone();
                tauri::async_runtime::block_on(async move {
                    let _ = p_mgr.terminate(app).await;
                });
            }
        });
}
