//! Frpc-Android 只产出 Android 安装包，仓库里不再保留桌面端图标（`.ico` / `.icns`）。
//!
//! 但在 Windows 开发机上执行 `cargo check` / `cargo test` 时，目标三元组是
//! `*-pc-windows-*`，`tauri-build` 仍会生成 Windows 资源文件，并且强制要求一个
//! `.ico`（见 `tauri-build` 的 `windows` 分支）。为了让主机侧的编译与单元测试
//! 可用，这里在 `OUT_DIR` 里写一个 1x1 的透明占位图标，不落到源码目录。

use std::env;
use std::fs;
use std::path::PathBuf;

fn main() {
    let mut attributes = tauri_build::Attributes::default();

    if env::var("TARGET")
        .map(|target| target.contains("windows"))
        .unwrap_or(false)
    {
        attributes = attributes.windows_attributes(tauri_build::WindowsAttributes::new().window_icon_path(
            placeholder_icon(&PathBuf::from(env::var("OUT_DIR").expect("missing OUT_DIR"))),
        ));
    }

    if let Err(error) = tauri_build::try_build(attributes) {
        panic!("failed to run tauri-build: {error:#}");
    }
}

/// 写出一个最小的合法 ICO（1x1、32bpp、全透明），返回其路径。
fn placeholder_icon(out_dir: &PathBuf) -> PathBuf {
    let mut bytes: Vec<u8> = Vec::with_capacity(70);

    // ICONDIR
    bytes.extend_from_slice(&[0x00, 0x00]); // reserved
    bytes.extend_from_slice(&[0x01, 0x00]); // type: icon
    bytes.extend_from_slice(&[0x01, 0x00]); // image count

    // ICONDIRENTRY
    bytes.extend_from_slice(&[0x01, 0x01, 0x00, 0x00]); // 1x1, 256 colors, reserved
    bytes.extend_from_slice(&[0x01, 0x00]); // color planes
    bytes.extend_from_slice(&[0x20, 0x00]); // 32 bits per pixel
    bytes.extend_from_slice(&48u32.to_le_bytes()); // bytes in resource
    bytes.extend_from_slice(&22u32.to_le_bytes()); // image offset

    // BITMAPINFOHEADER（高度翻倍：像素 + AND 掩码）
    bytes.extend_from_slice(&40u32.to_le_bytes());
    bytes.extend_from_slice(&1i32.to_le_bytes());
    bytes.extend_from_slice(&2i32.to_le_bytes());
    bytes.extend_from_slice(&[0x01, 0x00]); // planes
    bytes.extend_from_slice(&[0x20, 0x00]); // bit count
    bytes.extend_from_slice(&0u32.to_le_bytes()); // BI_RGB
    bytes.extend_from_slice(&0u32.to_le_bytes()); // size image
    bytes.extend_from_slice(&[0x00; 16]); // 分辨率与调色板信息全部置 0

    bytes.extend_from_slice(&[0x00; 4]); // BGRA 像素（透明）
    bytes.extend_from_slice(&[0x00; 4]); // AND 掩码，按 4 字节对齐补齐

    let path = out_dir.join("placeholder-icon.ico");
    fs::write(&path, bytes).expect("failed to write placeholder icon");
    path
}
