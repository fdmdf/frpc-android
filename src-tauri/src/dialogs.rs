//! 文件选择对话框抽象。
//!
//! 桌面端继续使用 rfd 的原生对话框；移动端没有 rfd，统一退化为“取消”，
//! 相应入口在界面上对移动端隐藏。

use std::path::PathBuf;

#[cfg(desktop)]
pub fn pick_file(filter_name: &str, extensions: &[&str]) -> Option<PathBuf> {
    let mut dialog = rfd::FileDialog::new();
    if !extensions.is_empty() {
        dialog = dialog.add_filter(filter_name, extensions);
    }
    dialog.pick_file()
}

#[cfg(desktop)]
pub fn save_file(default_name: &str, filter_name: &str, extensions: &[&str]) -> Option<PathBuf> {
    let mut dialog = rfd::FileDialog::new().set_file_name(default_name);
    if !extensions.is_empty() {
        dialog = dialog.add_filter(filter_name, extensions);
    }
    dialog.save_file()
}

#[cfg(mobile)]
pub fn pick_file(_filter_name: &str, _extensions: &[&str]) -> Option<PathBuf> {
    None
}

#[cfg(mobile)]
pub fn save_file(_default_name: &str, _filter_name: &str, _extensions: &[&str]) -> Option<PathBuf> {
    None
}
