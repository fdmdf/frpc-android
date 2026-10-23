<div align="center">
  <img src="docs/assets/icon.svg" width="112" height="112" alt="Frpc 客户端" />
  <h1>Frpc 客户端 · Android</h1>
  <p><b>可视化配置 · 一键启停 · 实时日志 · 运行监控</b><br>把 frp 内网穿透装进手机，像用桌面客户端一样管理 frpc</p>

  <p>
    <img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="License" />
    <img src="https://img.shields.io/badge/Android-8.0%2B%20(API%2026)-3DDC84?logo=android&logoColor=white" alt="Android" />
    <img src="https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
    <img src="https://img.shields.io/badge/Compose-BOM%202026.06-4285F4?logo=jetpackcompose&logoColor=white" alt="Compose" />
    <img src="https://img.shields.io/badge/frpc-v0.71.1-orange" alt="frp" />
  </p>

  <p>
    <a href="https://github.com/OWNER/REPO/releases/latest"><img src="https://img.shields.io/github/v/release/OWNER/REPO?label=%E4%B8%8B%E8%BD%BD%20APK" alt="Release" /></a>
    <a href="https://github.com/OWNER/REPO/actions/workflows/ci.yml"><img src="https://github.com/OWNER/REPO/actions/workflows/ci.yml/badge.svg" alt="CI" /></a>
  </p>

  <p><a href="README.en.md">English</a> · <b>简体中文</b> · <a href="docs/wiki/Home.md">Wiki</a></p>
</div>

---

## 目录

- [为什么做这个项目](#为什么做这个项目)
- [功能特性](#功能特性)
- [截图](#截图)
- [快速开始](#快速开始)
- [frp 兼容性](#frp-兼容性)
- [技术架构](#技术架构)
- [从源码构建](#从源码构建)
- [自动化发版](#自动化发版)
- [项目结构](#项目结构)
- [路线图](#路线图)
- [已知限制](#已知限制)
- [参与贡献](#参与贡献)
- [许可证与致谢](#许可证与致谢)

## 为什么做这个项目

[fatedier/frp](https://github.com/fatedier/frp) 是优秀的反向代理工具，但在手机上长期运行 frpc 一直很别扭：要么手写 `frpc.toml` 再配合终端模拟器，要么用一些只提供「启动/停止」按钮的壳应用。

本项目参考桌面端 [frpc-desktop](https://github.com/luckjiawei/frpc-desktop) 的思路，把**可视化配置、进程守护、日志、监控**完整搬到 Android：

- **原生实现**：Kotlin + Jetpack Compose，无 WebView 运行时，启动快、包体小（Release APK 约 7.5MB，其中 6MB 是 frpc 引擎本身）
- **跨平台逻辑层**：Kotlin Multiplatform，配置模型 / TOML 编解码 / 状态接口客户端 / 进程监控在 Android 与 JVM 双端复用，并有双端单元测试
- **真机可用**：适配 Android 10+ 的执行限制（W^X）、前台服务保活、开机自启、电池优化引导

## 功能特性

| 模块 | 能力 |
| --- | --- |
| 配置管理 | 多服务器档案、代理规则全类型（tcp/udp/http/https/stcp/sudp/xtcp/tcpmux）、访问者（visitor）、按类型动态表单、必填校验、端口冲突预检、`frpc.toml` 导入/导出 |
| 运行控制 | 前台服务常驻、优雅停止、配置热重载、进程崩溃自动重启（指数退避，最多 5 次）、开机自启 |
| 状态监控 | 代理在线状态（状态接口优先、日志推断兜底）、CPU/内存实时采样、运行时长、线程数、进程状态 |
| 日志 | frpc 输出实时解析（级别/来源/时间）、3000 行环形缓冲、级别过滤、自动滚动、复制分享 |
| 通知栏 | 常驻摘要（服务器 / 在线代理数 / 运行时长 / CPU / 内存），5 秒刷新，一键停止 |
| 官方 Web 控制台 | frpc 内置的官方控制台已打包，手机浏览器打开 `http://127.0.0.1:7400/static/` 即可使用 |
| 协议支持 | tcp / kcp / quic / websocket / wss 传输，TLS 开关，代理级加密与压缩 |
| 导入兼容 | 可直接导入 frp 官方示例配置；导入时自动清理该代理类型不支持的字段（避免 frpc 启动失败） |

## 截图

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/home.jpg" width="240" /><br><sub>运行状态与代理</sub></td>
    <td align="center"><img src="docs/screenshots/config-edit.jpg" width="240" /><br><sub>可视化配置</sub></td>
    <td align="center"><img src="docs/screenshots/logs.jpg" width="240" /><br><sub>实时日志</sub></td>
    <td align="center"><img src="docs/screenshots/settings.jpg" width="240" /><br><sub>引擎与设置</sub></td>
  </tr>
</table>

## 快速开始

1. 从 [Releases](https://github.com/OWNER/REPO/releases/latest) 下载 `frpc-android-*.apk`
2. 允许「未知来源」安装（小米/OPPO 等机型会提示未审核，可继续安装）
3. 打开 App → **配置** → 新建配置，填写 frps 地址、端口与 Token
4. 添加代理规则（例如 TCP：本地 22 → 远程 6001）→ 保存
5. 回**主页**点「启动」

> 建议在配置中保持「状态监控（本机接口）」开启：App 借此显示代理实际端口、在线状态与 CPU/内存。它只监听 `127.0.0.1`，不对外暴露。

详细配置说明见 [Wiki：配置指南](docs/wiki/配置指南.md)。

## frp 兼容性

内置 frpc **v0.71.1**。以下矩阵在 frps v0.71.1（ImmortalWrt/OpenWrt x86）上用真实 frpc 逐项实测（登录 + 状态接口 + 真实数据回环）：

| 能力 | 结果 | 服务端要求 |
| --- | --- | --- |
| tcp 传输（TLS 开/关） | ✓ | 无 |
| websocket 传输 | ✓ | 无 |
| kcp 传输 | ✓ | `kcp_bind_port` **必须等于**客户端 `serverPort` |
| quic 传输 | ✓ | `quic_bind_port` **必须等于**客户端 `serverPort`（如 7003） |
| stcp / xtcp 代理 | ✓ | 无（visitor 全流程实测） |
| sudp 代理 | ✓ | 无（visitor 模式，不占服务端 UDP 端口） |
| http 代理 | ✓ | `vhost_http_port` + `customDomains` |
| https 代理 | ✓ | `vhost_https_port`；本地服务须为 HTTPS（TLS 透传） |
| tcpmux 代理 | ✓ | `tcpmux_httpconnect_port` |
| wss 传输 | ✗ | frps 自身 TLS 下不可用；需前置 nginx/CDN 做 TLS 终止 |

**关键结论**：frpc 的 `protocol=kcp/quic` 不会自动改写端口，始终拨 `serverAddr:serverPort`，因此服务端监听端口必须与客户端填写的一致。详见 [协议与兼容性](docs/wiki/协议兼容性.md)。

## 技术架构

```
┌──────────────────────────── shared (Kotlin Multiplatform) ────────────────────────────┐
│  配置模型 FrpcConfig/ProxyConfig   TOML 编解码(ktoml)   配置校验器                      │
│  状态接口客户端(HttpURLConnection)  /proc 进程监控(CPU/内存)                            │
│  测试：commonTest（双端运行）+ jvmCommonTest（本地 HTTP 服务集成测试）                   │
└───────────────────────────────────┬───────────────────────────────────────────────────┘
                    android           │            jvm
┌───────────────────────────────────▼───────────────────────────────────────────────────┐
│ androidApp                                                                            │
│  FrpcService（specialUse 前台服务）  FrpcSupervisor（进程守护/日志/状态轮询/资源采样）     │
│  Compose UI（主页/配置/日志/监控/设置） ConfigRepository（JSON 原子写）                   │
│  jniLibs/arm64-v8a/libfrpc.so（frp 官方源码交叉编译，安装时解压到 nativeLibraryDir 执行）  │
└───────────────────────────────────────────────────────────────────────────────────────┘
```

设计要点：

- **为什么引擎放 jniLibs**：Android 10+ 禁止从应用数据目录执行二进制（W^X），jniLibs 安装时会解压到 `nativeLibraryDir`，是唯一稳定可执行的落点
- **为什么状态要「双源」**：状态接口（webServer）能给出实际分配端口与精确状态；关闭时用日志推断（`proxy added` / `start proxy success` / `start error`）兜底
- **为什么时间固定北京时间**：日志与监控对所有用户展示一致，不受设备时区影响

更多细节见 [Wiki：架构说明](docs/wiki/架构.md)。

## 从源码构建

环境要求：**JDK 17+**、**Android SDK**（platform 36 / build-tools 36.0.0）、**Go 1.26+**（仅重建引擎时需要）。

```bash
# 1. SDK 路径
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

# 2. 单测 + Debug 包
./gradlew :shared:jvmTest :shared:testDebugUnitTest :androidApp:testDebugUnitTest
./gradlew :androidApp:assembleDebug

# 3. Release（未配置签名时产出 unsigned APK）
KEYSTORE_PATH=/path/to/release.jks KEYSTORE_PASSWORD=*** KEY_ALIAS=frpc KEY_PASSWORD=*** \
  ./gradlew :androidApp:assembleRelease

# 4. 重建 frpc 引擎（可选，默认使用仓库内已编译产物）
FRP_VERSION=v0.71.1 bash scripts/build-frpc.sh
```

> Release 签名通过环境变量注入（`KEYSTORE_PATH` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`），未提供时构建保持未签名，便于本地验证。

## 自动化发版

`.github/workflows/release.yml` 实现「frp 有新版本 → 自动发版」：

1. 每日 03:00 UTC 检查 [frp releases](https://github.com/fatedier/frp/releases)
2. 发现新版本 → 交叉编译 frpc（android/arm64）→ 提交引擎更新回 `main`
3. 运行全部单测 → 构建签名 APK
4. 创建 Release（tag `frp-<version>`）并上传 `frpc-android-<app>-frp<version>.apk`

手动发版：打 tag 推送（`git tag v1.1.0 && git push origin v1.1.0`），或 Actions 页面手动触发并指定 frp 版本。

需要在仓库 Secrets 中配置（不配置则产出未签名 APK）：

| Secret | 说明 |
| --- | --- |
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` 的结果 |
| `KEYSTORE_PASSWORD` | keystore 密码 |
| `KEY_ALIAS` | 签名别名 |
| `KEY_PASSWORD` | 别名密码 |

`ci.yml` 在每次 push / PR 时运行双端单测、lint（非阻塞）与 Debug 构建，并校验引擎构建脚本可复现。

## 项目结构

```
├── shared/                      # KMP 逻辑层（Android + JVM 双端复用）
│   ├── commonMain/              # 配置模型、TOML 编解码、校验器、状态接口模型
│   ├── jvmCommonMain/           # 状态接口客户端、/proc 进程监控
│   ├── commonTest/              # 纯逻辑测试（双端运行）
│   └── jvmCommonTest/           # 依赖 JVM API 的测试（本地 HTTP 集成测试）
├── androidApp/
│   └── src/main/
│       ├── java/com/frpc/android/    # Service / Supervisor / Compose UI
│       ├── jniLibs/arm64-v8a/        # frpc 引擎（frp 源码交叉编译）
│       └── assets/frpc/VERSION       # 引擎版本号
├── scripts/
│   ├── build-frpc.sh            # 引擎交叉编译脚本（可复现）
│   ├── frpc-web-dist/           # 官方 Web 控制台构建产物（嵌入引擎）
│   └── publish.sh               # 一键初始化 GitHub 仓库并推送
├── docs/
│   ├── assets/icon.svg          # 项目图标
│   ├── screenshots/             # README 截图
│   └── wiki/                    # Wiki 页面源文件（可一键发布）
└── .github/workflows/           # CI 与自动发版
```

## 路线图

- [ ] 支持 xtcp 的 P2P 打洞参数与 visitor 高级选项
- [ ] 插件（plugin）与健康检查（healthCheck）可视化配置
- [ ] iOS / 桌面端（复用 `shared` 逻辑层）
- [ ] 多配置并行运行
- [ ] 流量统计图表（基于 frps API）

## 已知限制

- 导入 `frpc.toml` 时，未建模字段会被丢弃（plugin / healthCheck / annotations / metadatas / featureGates / includes / loadBalancer）
- 仅支持 **arm64-v8a**（Go 对 android/arm 32 位不支持纯静态链接，上游 frp 亦只提供 arm64）；x86_64 可在构建时可选开启
- wss 传输需在 frps 前置 TLS 反代（见兼容性矩阵）
- 未在本机运行 Android 设备级自动化测试（UI 走查依赖真机/模拟器）

## 参与贡献

欢迎 Issue / PR。请先阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 与 [行为准则](CODE_OF_CONDUCT.md)。

- Bug 反馈请附带：机型 / Android 版本 / App 版本 / 相关日志（App 内「分享」按钮导出）
- 提交代码请确保 `./gradlew :shared:jvmTest :androidApp:testDebugUnitTest` 全绿
- 安全问题请按 [SECURITY.md](SECURITY.md) 私下报告

## 许可证与致谢

- 本项目代码：[MIT](LICENSE)
- frpc 引擎：[fatedier/frp](https://github.com/fatedier/frp)（Apache-2.0），随包分发并在 [NOTICE](NOTICE) 中声明
- 界面与功能设计参考：[frpc-desktop](https://github.com/luckjiawei/frpc-desktop)
- 图标与界面：Jetpack Compose Material 3

如果这个项目对你有帮助，欢迎点一个 Star。
