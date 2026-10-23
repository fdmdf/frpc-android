#!/usr/bin/env bash
# 交叉编译 frpc（frp v0.71.1）为 Android 二进制，产物放入 app/src/main/assets/frpc/<abi>/frpc
#
# 用法：
#   bash scripts/build-frpc.sh                    # 仅 arm64-v8a（默认，安装包最小）
#   SKIP_X86_64=0 bash scripts/build-frpc.sh      # 额外编译 x86_64（模拟器调试）
#   GOPROXY=https://goproxy.cn,direct bash scripts/build-frpc.sh
#
# 依赖：Go >= 1.26（frp go.mod 要求）；网络可访问 GOPROXY；编译 x86_64 另需 Android NDK
# 说明：
#   - armeabi-v7a 未提供：Go 对 android/arm 32 位不支持 CGO_ENABLED=0 静态链接，
#     上游 frp 官方发布版同样只提供 android/arm64。
#   - x86_64 仅供模拟器调试，会显著增加 APK 体积，默认不编。
#   - web/frpc/dist 用占位文件补齐：安卓客户端只调用 admin JSON API，不需要 Web 面板；
#     如需 frpc 自带 Web 面板，请先在 web/frpc 下执行 npm install && npm run build。

set -euo pipefail

FRP_VERSION="${FRP_VERSION:-v0.71.1}"
GO="${GO:-go}"
export GOPROXY="${GOPROXY:-https://goproxy.cn,direct}"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$REPO_ROOT/app/src/main/assets/frpc"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

echo "==> frp version: $FRP_VERSION"
echo "==> fetching source via module proxy"

SRC_DIR="$WORK_DIR/frp-src"
mkdir -p "$SRC_DIR"
curl -sSL --max-time 300 -o "$WORK_DIR/frp.zip" \
  "https://goproxy.cn/github.com/fatedier/frp/@v/${FRP_VERSION}.zip"
python3 -c "import zipfile,sys; zipfile.ZipFile('$WORK_DIR/frp.zip').extractall('$SRC_DIR')"
FRP_SRC="$SRC_DIR/github.com/fatedier/frp@${FRP_VERSION}"

echo "==> patching web/frpc/dist placeholder for go:embed"
mkdir -p "$FRP_SRC/web/frpc/dist"
printf '<!doctype html><title>frpc</title><p>admin web assets not bundled; JSON API available.</p>\n' \
  > "$FRP_SRC/web/frpc/dist/index.html"

cd "$FRP_SRC"

build() {
  local abi="$1" goarch="$2" goarm="$3"
  echo "==> building $abi (GOOS=android GOARCH=$goarch${goarm:+ GOARM=$goarm})"
  mkdir -p "$OUT/$abi"
  if [ -n "$goarm" ]; then
    CGO_ENABLED=1 CC="$(android_clang "$abi")" \
      GOOS=android GOARCH="$goarch" GOARM="$goarm" \
      "$GO" build -trimpath -ldflags "-s -w" -tags frpc -o "$OUT/$abi/frpc" ./cmd/frpc
  else
    CGO_ENABLED=0 \
      GOOS=android GOARCH="$goarch" \
      "$GO" build -trimpath -ldflags "-s -w" -tags frpc -o "$OUT/$abi/frpc" ./cmd/frpc
  fi
}

android_clang() {
  local ndk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/c/Users/Liao/AppData/Local/Android/Sdk}}/ndk"
  local latest
  latest="$(ls "$ndk" 2>/dev/null | sort -V | tail -1)"
  if [ -z "$latest" ]; then
    echo "NDK not found under $ndk" >&2
    exit 1
  fi
  echo "$ndk/$latest/toolchains/llvm/prebuilt/windows-x86_64/bin"
}

# arm64-v8a：纯静态，无 NDK 依赖
build arm64-v8a arm64 ""

# x86_64：android/amd64 需 cgo 外链（模拟器用，需要 NDK；SKIP_X86_64=0 时启用）
if [ "${SKIP_X86_64:-1}" != "1" ]; then
  NDK_BIN="$(android_clang x86_64)"
  echo "==> building x86_64 (cgo via NDK)"
  mkdir -p "$OUT/x86_64"
  CGO_ENABLED=1 CC="$NDK_BIN/x86_64-linux-android21-clang" \
    GOOS=android GOARCH=amd64 \
    "$GO" build -trimpath -ldflags "-s -w" -tags frpc -o "$OUT/x86_64/frpc" ./cmd/frpc
fi

# armeabi-v7a：Go 不支持纯静态链接；如需支持必须走 NDK cgo（体积与兼容性权衡后未纳入）
# build armeabi-v7a arm 7  # 取消注释并确保 NDK 可用

echo "$FRP_VERSION" > "$OUT/VERSION"
echo "==> done:"
ls -la "$OUT"/*/
