//! 系统级能力：日志读取、外部链接、GitHub 更新检查。
//!
//! 桌面端的 CPU/内存占用轮询（sysinfo）与文件管理器打开目录（open crate）已移除：
//! Android 上没有对应的可靠实现，打开外部链接统一走 Kotlin 插件。

use std::fs::File;
use std::io::{BufRead, BufReader};
use std::path::Path;
use std::time::Duration;

pub struct SystemService;

impl SystemService {
    /// 用系统浏览器打开链接。
    ///
    /// Android 需要 AppHandle 才能走 Kotlin 侧的原生插件：`open` crate 依赖 `am`
    /// 命令，在 Android 上不可用。
    #[cfg(target_os = "android")]
    pub fn open_url(app: &tauri::AppHandle, url: &str) -> anyhow::Result<()> {
        if crate::android::open_url(app, url) {
            Ok(())
        } else {
            anyhow::bail!("open url failed")
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

    /// 检查本仓库（fdmdf/frpc-android）的最新 Release，用于提示 APK 更新。
    pub async fn check_latest_release() -> Option<serde_json::Value> {
        let client = reqwest::Client::builder()
            .user_agent(concat!("frpc-android/", env!("CARGO_PKG_VERSION")))
            .timeout(Duration::from_secs(5))
            .build()
            .ok()?;

        let urls = [
            "https://gh.jwinks.com/api/repos/fdmdf/frpc-android/releases/latest",
            "https://api.github.com/repos/fdmdf/frpc-android/releases/latest",
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
