#!/usr/bin/env python3
"""用 PC 端同一张源图标生成 Android 全套启动器图标。

`tauri android init` 生成的是 Android Studio 默认模板（绿色底 + 机器人），
本项目图标不会自动同步过去，所以每次换图标都要跑一次本脚本。

用法：
    python scripts/gen-android-icons.py [源图标路径]

产物：
    src-tauri/gen/android/app/src/main/res/mipmap-*/ic_launcher.png
                                              ic_launcher_round.png
                                              ic_launcher_foreground.png
    src-tauri/icons/android/                  （同步一份，供重新 android init 使用）
"""
import sys
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
SRC = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "src-tauri" / "icons" / "icon.png"

# 密度 -> (启动器图标边长, adaptive 前景边长)
# 启动器图标按 48dp 基准；adaptive 前景按 108dp 基准（432 = 108 * 4）
DENSITY = {
    "mdpi": (48, 108),
    "hdpi": (72, 162),
    "xhdpi": (96, 216),
    "xxhdpi": (144, 324),
    "xxxhdpi": (192, 432),
}

TARGETS = [
    ROOT / "src-tauri" / "gen" / "android" / "app" / "src" / "main" / "res",
    ROOT / "src-tauri" / "icons" / "android",
]

src = Image.open(SRC).convert("RGBA")
print(f"源图标: {SRC} ({src.size[0]}x{src.size[1]})")


def round_icon(img: Image.Image) -> Image.Image:
    """把图标裁成圆形（圆外透明），供圆形启动器使用。"""
    size = img.size[0]
    scale = 4  # 先放大画圆再缩回，边缘更平滑
    mask = Image.new("L", (size * scale, size * scale), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, size * scale - 1, size * scale - 1), fill=255)
    mask = mask.resize((size, size), Image.LANCZOS)
    out = img.copy()
    out.putalpha(ImageChops.multiply(img.getchannel("A"), mask))
    return out


for res_dir in TARGETS:
    for density, (launcher, foreground) in DENSITY.items():
        out = res_dir / f"mipmap-{density}"
        out.mkdir(parents=True, exist_ok=True)

        icon = src.resize((launcher, launcher), Image.LANCZOS)
        icon.save(out / "ic_launcher.png")
        round_icon(icon).save(out / "ic_launcher_round.png")
        src.resize((foreground, foreground), Image.LANCZOS).save(
            out / "ic_launcher_foreground.png"
        )
    print(f"已生成: {res_dir}")

print("完成。背景色见 drawable/ic_launcher_background.xml（白色，与 PC 端图标底色一致）。")
