pub mod commands;
pub mod config_gen;
pub mod db;
pub mod dialogs;
pub mod downloader;
pub mod models;
pub mod process;
pub mod system;

#[cfg(target_os = "android")]
pub mod android;

use commands::{ipc_send, AppState};
use db::DbManager;
use downloader::VersionManager;
use process::ProcessManager;
use std::fs;
use std::path::PathBuf;
#[cfg(desktop)]
use system::SystemService;
use tauri::Manager;

#[cfg(desktop)]
use tauri::menu::{Menu, MenuItem};
#[cfg(desktop)]
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};

/// 桌面端沿用 Electron 1.x 的 userData 目录，保证升级后 SQLite、日志、
/// 已下载的 frpc 都还在；移动端 `dirs::config_dir()` 返回 None，改用应用私有目录。
fn resolve_base_dir(app: &tauri::AppHandle) -> PathBuf {
    #[cfg(desktop)]
    {
        let _ = app;
        dirs::config_dir()
            .unwrap_or_else(|| PathBuf::from("."))
            .join("Frpc-Desktop")
    }

    #[cfg(mobile)]
    {
        app.path()
            .app_data_dir()
            .unwrap_or_else(|_| PathBuf::from("."))
    }
}

#[cfg(desktop)]
fn setup_tray(app: &tauri::App) -> tauri::Result<()> {
    let show_i = MenuItem::with_id(app, "show", "显示主窗口", true, None::<&str>)?;
    let quit_i = MenuItem::with_id(app, "quit", "退出", true, None::<&str>)?;
    let menu = Menu::with_items(app, &[&show_i, &quit_i])?;

    let tray_builder = TrayIconBuilder::new()
        .menu(&menu)
        .show_menu_on_left_click(false)
        .on_menu_event(|app, event| match event.id().as_ref() {
            "show" => {
                #[cfg(target_os = "macos")]
                {
                    let _ = app.set_activation_policy(tauri::ActivationPolicy::Regular);
                    let _ = app.set_dock_visibility(true);
                }
                if let Some(window) = app.get_webview_window("main") {
                    let _ = window.unminimize();
                    let _ = window.show();
                    let _ = window.set_focus();
                }
            }
            "quit" => {
                let state = app.state::<AppState>();
                let p_mgr = state.process_mgr.clone();
                let app_handle = app.clone();
                tauri::async_runtime::spawn(async move {
                    let _ = p_mgr.terminate(app_handle.clone()).await;
                    app_handle.exit(0);
                });
            }
            _ => {}
        })
        .on_tray_icon_event(|tray, event| {
            if let TrayIconEvent::Click {
                button: MouseButton::Left,
                button_state: MouseButtonState::Up,
                ..
            } = event
            {
                let app = tray.app_handle();
                #[cfg(target_os = "macos")]
                {
                    let _ = app.set_activation_policy(tauri::ActivationPolicy::Regular);
                    let _ = app.set_dock_visibility(true);
                }
                if let Some(window) = app.get_webview_window("main") {
                    let _ = window.unminimize();
                    let _ = window.show();
                    let _ = window.set_focus();
                }
            }
        });

    let tray_icon =
        tauri::image::Image::from_bytes(include_bytes!("../../public/logo/only/32x32.png")).ok();
    let icon_ref = tray_icon.as_ref().or_else(|| app.default_window_icon());

    if let Some(icon) = icon_ref {
        let _ = tray_builder.icon(icon.clone()).build(app)?;
    } else {
        let _ = tray_builder.build(app)?;
    }

    Ok(())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let mut builder = tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_opener::init());

    #[cfg(desktop)]
    {
        builder = builder.plugin(tauri_plugin_autostart::init(
            tauri_plugin_autostart::MacosLauncher::LaunchAgent,
            Some(vec!["--silent"]),
        ));
    }

    #[cfg(target_os = "android")]
    {
        builder = builder.plugin(android::init());
    }

    builder
        .on_window_event(|window, event| {
            // 移动端窗口就是 Activity，拦截关闭会导致无法退出。
            #[cfg(desktop)]
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                let _ = window.hide();
                #[cfg(target_os = "macos")]
                {
                    let app = window.app_handle();
                    let _ = app.set_dock_visibility(false);
                    let _ = app.set_activation_policy(tauri::ActivationPolicy::Accessory);
                }
            }
            #[cfg(mobile)]
            {
                let _ = (window, event);
            }
        })
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
            let version_mgr = VersionManager::new(&base_dir);

            let config_path = config_dir.join("frpc.toml");
            let frpc_log_path = log_dir.join("frpc.log");
            let app_log_path = log_dir.join("main.log");

            let app_state = AppState {
                db,
                process_mgr: process_mgr.clone(),
                version_mgr,
                app_data_dir: base_dir,
                config_path,
                frpc_log_path,
                app_log_path,
            };

            app.manage(app_state);

            // 系统占用轮询依赖 sysinfo，移动端没有对应实现，直接跳过。
            #[cfg(desktop)]
            SystemService::start_usage_poller(app.handle().clone());

            #[cfg(desktop)]
            setup_tray(app)?;

            // 配置里主窗口是 visible:false（桌面端按需显示）；移动端没有这层逻辑，
            // 必须显式显示，否则会看到一个空白的 Activity。
            #[cfg(mobile)]
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.show();
            }

            let server_cfg = {
                let state = app.state::<AppState>();
                let conn = state.db.conn();
                let res = db::server_repo::ServerRepository::get_server_config(&conn).ok();
                res
            };

            let is_silent = std::env::args().any(|a| a == "--silent");
            if let Some(server) = server_cfg {
                #[cfg(desktop)]
                {
                    use tauri_plugin_autostart::ManagerExt;
                    if server.system.launch_at_startup {
                        let _ = app.autolaunch().enable();
                    } else {
                        let _ = app.autolaunch().disable();
                    }
                }

                #[cfg(desktop)]
                {
                    if !is_silent && !server.system.silent_startup {
                        #[cfg(target_os = "macos")]
                        {
                            let _ = app
                                .handle()
                                .set_activation_policy(tauri::ActivationPolicy::Regular);
                            let _ = app.handle().set_dock_visibility(true);
                        }
                        if let Some(window) = app.get_webview_window("main") {
                            let _ = window.show();
                            let _ = window.set_focus();
                        }
                    } else {
                        #[cfg(target_os = "macos")]
                        {
                            let _ = app
                                .handle()
                                .set_dock_visibility(false);
                            let _ = app
                                .handle()
                                .set_activation_policy(tauri::ActivationPolicy::Accessory);
                        }
                    }
                }

                #[cfg(mobile)]
                {
                    let _ = is_silent;
                }

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
            } else {
                #[cfg(desktop)]
                if let Some(window) = app.get_webview_window("main") {
                    #[cfg(target_os = "macos")]
                    {
                        let _ = app
                            .handle()
                            .set_activation_policy(tauri::ActivationPolicy::Regular);
                        let _ = app.handle().set_dock_visibility(true);
                    }
                    let _ = window.show();
                    let _ = window.set_focus();
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
