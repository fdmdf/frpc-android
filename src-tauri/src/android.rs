//! Android 适配层。
//!
//! Android 10+ 禁止从应用数据目录 `execve`（SELinux 限制），因此 frpc 不能像
//! 桌面端那样下载到用户目录再执行。这里的做法是把 frpc 交叉编译成 `libfrpc.so`
//! 随 APK 一起安装，运行时从 `nativeLibraryDir` 读取并执行——该目录具备可执行权限。
//!
//! 另外，Android 会把后台进程冻结/回收，所以 frpc 运行期间需要拉起一个前台服务
//! 保活，这部分由 Kotlin 侧的 `FrpcForegroundService` 完成。

use serde::Deserialize;
use serde_json::json;
use std::path::PathBuf;
use tauri::plugin::{Builder, PluginHandle, TauriPlugin};
use tauri::{AppHandle, Manager, Runtime, Wry};

/// 插件标识符必须等于 Kotlin 插件所在的 **Java 包名**：`register_android_plugin` 内部会
/// 拼接成 `{identifier}.{className}` 再 `Class.forName`，写成 `frpc-android` 这类名字会
/// 抛 `ClassNotFoundException`，进而让整个 setup 失败、应用启动即崩溃。
const PLUGIN_NAME: &str = "priv.liujiawei.frpc.desktop";
/// Kotlin 插件类的简单类名。
const PLUGIN_CLASS: &str = "FrpcServicePlugin";
/// APK 内打包的 frpc 二进制名（必须以 lib 开头、.so 结尾才会被安装解压）。
pub const FRPC_LIB_NAME: &str = "libfrpc.so";

pub struct FrpcService<R: Runtime>(PluginHandle<R>);

impl<R: Runtime> FrpcService<R> {
    fn call(&self, command: &str, payload: serde_json::Value) -> Result<serde_json::Value, String> {
        self.0
            .run_mobile_plugin::<serde_json::Value>(command, payload)
            .map_err(|e| e.to_string())
    }
}

/// Kotlin 侧返回的运行环境信息。
#[derive(Clone, Debug, Deserialize)]
pub struct AppInfo {
    #[serde(rename = "nativeLibDir")]
    pub native_lib_dir: String,
    #[serde(rename = "sdkInt")]
    pub sdk_int: i32,
}

impl AppInfo {
    /// 安装后 frpc 的实际路径。
    pub fn frpc_binary(&self) -> PathBuf {
        PathBuf::from(&self.native_lib_dir).join(FRPC_LIB_NAME)
    }
}

pub fn init() -> TauriPlugin<Wry> {
    Builder::new(PLUGIN_NAME)
        .setup(|app, api| {
            // 注册失败不能让应用起不来：只是拿不到前台服务/内置 frpc 路径，
            // 界面仍应正常展示，便于用户看到错误提示。
            match api.register_android_plugin(PLUGIN_NAME, PLUGIN_CLASS) {
                Ok(handle) => {
                    app.manage(FrpcService(handle));
                }
                Err(e) => log::error!("register {} failed: {}", PLUGIN_CLASS, e),
            }
            Ok(())
        })
        .build()
}

fn service(app: &AppHandle) -> Option<tauri::State<'_, FrpcService<Wry>>> {
    app.try_state::<FrpcService<Wry>>()
}

/// 查询 Kotlin 侧信息。调用会产生一次 JNI 往返，只在启动 frpc 时用一次。
pub fn app_info(app: &AppHandle) -> Option<AppInfo> {
    let service = service(app)?;
    match service.0.run_mobile_plugin::<AppInfo>("getAppInfo", json!({})) {
        Ok(info) => Some(info),
        Err(e) => {
            log::warn!("getAppInfo failed: {}", e);
            None
        }
    }
}

/// 内置 frpc 二进制路径，不存在时返回 None。
pub fn frpc_binary(app: &AppHandle) -> Option<PathBuf> {
    let info = app_info(app)?;
    let path = info.frpc_binary();
    if path.is_file() {
        Some(path)
    } else {
        log::error!("bundled frpc not found at {}", path.display());
        None
    }
}

/// 启动前台服务保活。失败只记录日志，不影响 frpc 本身运行。
pub fn start_service(app: &AppHandle) {
    if let Some(service) = service(app) {
        if let Err(e) = service.call("startService", json!({})) {
            log::warn!("start foreground service failed: {}", e);
        }
    }
}

pub fn stop_service(app: &AppHandle) {
    if let Some(service) = service(app) {
        if let Err(e) = service.call("stopService", json!({})) {
            log::warn!("stop foreground service failed: {}", e);
        }
    }
}

/// 更新前台服务通知中的状态文案。
pub fn update_notification(app: &AppHandle, running: bool) {
    if let Some(service) = service(app) {
        let _ = service.call("updateNotification", json!({ "running": running }));
    }
}

/// 用系统浏览器打开链接（`open` crate 在 Android 上依赖 `am` 命令，不可靠）。
pub fn open_url(app: &AppHandle, url: &str) -> bool {
    let Some(service) = service(app) else {
        return false;
    };
    match service.call("openUrl", json!({ "url": url })) {
        Ok(_) => true,
        Err(e) => {
            log::warn!("openUrl failed: {}", e);
            false
        }
    }
}
