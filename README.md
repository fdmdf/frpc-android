<a name="readme-top"></a>

<!-- PROJECT SHIELDS -->
[![Downloads][downloads-shield]][downloads-url]
[![Forks][forks-shield]][forks-url]
[![Stargazers][stars-shield]][stars-url]
[![Issues][issues-shield]][issues-url]
[![MIT License][license-shield]][license-url]

<!-- PROJECT LOGO -->
<br />
<div align="center">
  <a href="https://github.com/fdmdf/frpc-android">
    <img src="public/logo/pack/1024x1024.png" alt="Logo" width="140">
  </a>

<h3 align="center">Frpc-Android</h3>

  <p align="center">
    🎉 Android client for FRP — visual configuration, easy intranet penetration!
    <br />
    frpc built in / background keep-alive / visual configuration / free and open source
  </p>

  <p><a href="https://jwinks.com/p/frp/">📖 Docs</a> &nbsp; <a href="README.zh_CN.md">中文</a></p>
</div>

> 💡 Android-only fork of [luckjiawei/frpc-desktop](https://github.com/luckjiawei/frpc-desktop):
> same configuration model and frp support, all desktop (Windows / macOS / Linux) code removed.

<p align="center">
  <img src="docs/images/android-preview.svg" alt="Frpc-Android preview" width="280">
</p>

## ✨ Features

- **frpc built in** — ships as `libfrpc.so` (frp v0.71.1), no root, nothing to download
- **Stays connected** — foreground service + wake lock, backoff auto-reconnect (2s → 60s)
- **Mobile UI** — bottom navigation, single-column forms, safe-area padding for status/navigation bars
- **Full frp support** — server / transport / TLS / web server settings, all proxy types
- **Bilingual** — 简体中文 & English

## 📦 Download

[Latest release](https://github.com/fdmdf/frpc-android/releases/latest) →
`Frpc-Android-<version>-android-arm64.apk` (arm64, signed with a fixed key so updates install in place).

## 🚀 Quick start

1. Install the APK (grant "install unknown apps" to your browser or file manager).
2. **Config** → server address, port, auth token.
3. **Proxy** → add a proxy: type, local IP/port, remote port.
4. **Launch** → tap start; the status turns green once frpc is connected.

## 🛠 Build

Requirements: Node.js 22.12+, npm, Rust (with `aarch64-linux-android` target), JDK 17,
Android SDK + NDK, and Go 1.21+ for the frpc engine.

```sh
npm ci
npm run android:frpc   # cross-compile frpc into jniLibs
npm run build          # build the webview bundle (embedded into the Rust library)
npm run android:apk    # assemble the debug APK
npm run android:apk -- aarch64-linux-android --release   # signed release APK
```

Development on a device or emulator:

```sh
npm run android:dev
```

Build details, signing and troubleshooting: [docs/ANDROID.md](docs/ANDROID.md).

## 💬 Community

No advertisements allowed!!! Telegram: [https://t.me/+4kziSBL3LxVmYzVl](https://t.me/+4kziSBL3LxVmYzVl)

## ☕ Donate

👉👉👉[Click to donate](https://jwinks.com/donate/)👈👈👈

## License

[MIT](LICENSE)

<!-- MARKDOWN LINKS & IMAGES -->

[downloads-shield]: https://img.shields.io/github/downloads/fdmdf/frpc-android/total.svg?style=for-the-badge

[downloads-url]: https://github.com/fdmdf/frpc-android/releases

[forks-shield]: https://img.shields.io/github/forks/fdmdf/frpc-android.svg?style=for-the-badge

[forks-url]: https://github.com/fdmdf/frpc-android/network/members

[stars-shield]: https://img.shields.io/github/stars/fdmdf/frpc-android.svg?style=for-the-badge

[stars-url]: https://github.com/fdmdf/frpc-android/stargazers

[issues-shield]: https://img.shields.io/github/issues/fdmdf/frpc-android.svg?style=for-the-badge

[issues-url]: https://github.com/fdmdf/frpc-android/issues

[license-shield]: https://img.shields.io/github/license/fdmdf/frpc-android.svg?style=for-the-badge

[license-url]: https://github.com/fdmdf/frpc-android/blob/main/LICENSE
