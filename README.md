# Frpc 客户端（Android）

一个 **frpc 安卓客户端**：可视化配置 frp 内网穿透规则，一键启停、实时日志、运行监控。
界面与功能对齐 [luckjiawei/frpc-desktop](https://github.com/luckjiawei/frpc-desktop)（可视化配置、进程启停、资源监控、端口预检、持久化），并针对安卓平台增加前台服务保活、开机自启、通知栏状态等能力。

## 技术栈与选型理由

| 要求 | 方案 |
| --- | --- |
| 安装包小 | 原生 Kotlin + Compose，无 WebView/JS 运行时；应用代码压缩后约 2-3MB，其余体积为 frpc 引擎本体（Go 静态二进制，arm64 约 16.9MB） |
| 运行快 | 原生渲染，无桥接层；frpc 以子进程方式运行，与桌面版架构一致 |
| 跨平台 | **Kotlin Multiplatform**：`shared` 模块同一套代码编译到 **Android + JVM** 两个目标，业务逻辑（配置模型、TOML 编解码、admin API 客户端、/proc 监控）双端复用且双端跑单元测试；后续可低成本扩展 iOS/桌面目标 |

- UI：Jetpack Compose Material 3（动态取色、深浅色、中英文）
- 持久化：配置文件 JSON 原子写（`filesDir/configs.json`）+ SharedPreferences 设置
- 网络：`HttpURLConnection` + kotlinx.serialization（admin API）
- TOML：ktoml（KMP），含点分键兼容修复（见下）
- 无 Room/DI 框架/导航库依赖——保持包体与构建复杂度最小

## 功能

- **可视化配置**：服务器地址/端口、认证 Token、Admin API（webServer）、传输协议（tcp/kcp/quic/websocket/wss + TLS + tcpMux）、日志级别
- **代理规则 CRUD**：tcp / udp / http / https / stcp / sudp / xtcp / tcpmux 全类型，按类型动态表单；带宽限制、加密、压缩
- **访问者（visitor）**：stcp/xtcp visitor 配置
- **启停/重载**：前台服务（specialUse）保活；支持 `/api/reload` 热重载；进程崩溃自动重启（指数退避，最多 5 次）
- **实时日志**：frpc stdout 实时解析（级别/来源/时间），3000 行环形缓冲、级别过滤、复制分享
- **运行监控**：CPU 占用率（/proc 采样）、内存、PID、线程数、运行时长；代理在线状态（admin API `/api/status`）
- **端口冲突预检**：保存/启动前校验 admin 端口占用（对齐 frpc-desktop B1006）
- **frpc.toml 导入/导出**：粘贴或文件导入、分享导出；导入兼容 frpc 官方示例配置
- **开机自启**（BOOT_COMPLETED，失败降级通知提醒）、**通知栏常驻状态**（在线数/启停操作）
- 电池优化白名单引导、通知权限引导

## 工程结构

```
├── shared/                        # KMP：Android + JVM 双端逻辑层
│   └── src/
│       ├── commonMain/            # 配置模型、TOML 编解码、校验器、admin 模型
│       ├── jvmCommonMain/         # HttpURLConnection admin 客户端、/proc 进程监控
│       ├── commonTest/            # 纯逻辑测试（双端运行）
│       └── jvmCommonTest/         # 需要 JVM API 的测试（本地 HTTP 服务器集成测试）
├── androidApp/                    # 安卓应用（Compose UI + 前台服务 + 进程管理）
│   └── src/main/
│       ├── jniLibs/arm64-v8a/libfrpc.so  # Go 交叉编译的 frpc 引擎（安装时解压执行）
│       ├── assets/frpc/VERSION    # 引擎版本号（仅展示）
│       ├── java/com/frpc/android/ # Application/Activity/Service/Supervisor/UI
│       └── res/                   # 中英文资源、主题、图标
└── scripts/build-frpc.sh          # frpc 交叉编译脚本（可复现）
```

## 构建与运行

环境要求：**JDK 17+**、**Android SDK**（platform 36、build-tools 36.0.0、NDK 仅编译 x86_64 时需要）。

```bash
# 1. 配置 SDK 路径（已有则跳过）
echo "sdk.dir=/path/to/Android/sdk" > local.properties

# 2. 构建 Debug APK
./gradlew :androidApp:assembleDebug

# 3. shared 模块测试（Android + JVM 双端逻辑）
./gradlew :shared:jvmTest :shared:testDebugUnitTest

# 4. Release（未签名，仅供验证）
./gradlew :androidApp:assembleRelease
```

产物：`androidApp/build/outputs/apk/debug/androidApp-debug.apk`

安装到设备后：进入「配置」页新建服务器配置 → 填写 frps 地址与 Token → 添加代理规则 → 回主页点击「启动」。

## frpc 引擎兼容性（实测矩阵）

内置 frpc **v0.71.1**。以下矩阵在 **frps v0.71.1（ImmortalWrt 23.05 / OpenWRT，x86）** 上用真实 frpc 二进制逐项实测（登录 + 管理 API + 真实数据回环）：

| 能力 | 结果 | 服务端配置要求 |
|---|---|---|
| tcp 传输（TLS 开/关） | ✅ | 无（App 默认开 TLS） |
| websocket 传输 | ✅ | 无 |
| kcp 传输 | ✅ | **`kcp_bind_port` 必须等于客户端 `serverPort`** |
| quic 传输 | ✅ | **`quic_bind_port` 必须等于客户端 `serverPort`**（如 7003） |
| stcp / xtcp 代理 | ✅ | 无（visitor 全流程实测） |
| sudp 代理 | ✅ | 无 UDP 端口要求（visitor 模式，实测 UDP 回环） |
| http 代理 | ✅ | `vhost_http_port` + `customDomains` |
| https 代理 | ✅ | `vhost_https_port`；**本地服务必须本身是 HTTPS**（TLS 透传，frps 只按 SNI 路由） |
| tcpmux 代理 | ✅ | `tcpmux_httpconnect_port`（httpconnect 实测 CONNECT 隧道） |
| wss 传输 | ❌ | frps 自身 TLS 下不可用（服务端 TLS 解包后走 yamux，与 websocket 升级不兼容）；需在 frps 前置 nginx/CDN 做 TLS 终止 |

**关键结论**：frpc 的 `protocol=kcp/quic` 时**不会自动改写端口**，始终拨 `serverAddr:serverPort`。因此服务端 kcp/quic 监听端口必须与客户端填写的端口一致（同一台 frps 上 kcp 与 quic 需要一个占用主端口、另一个用独立端口，客户端按需切换）。

服务端缺配置时 frpc 不会退出，对应代理会显示 `start error` 及原因（App 主页/监控页可见）。

## frpc 引擎说明

- 版本：**frp v0.71.1**，由 `scripts/build-frpc.sh` 从官方源码交叉编译（`CGO_ENABLED=0 GOOS=android GOARCH=arm64`，strip 后约 16.9MB）
- **打包方式**：以 `libfrpc.so` 形式放入 `androidApp/src/main/jniLibs/arm64-v8a/`，安装时由系统解压到 `nativeLibraryDir` 后直接执行
  - 为什么不能放 assets 再拷到 filesDir：**Android 10+（targetSdk 29+）的 W^X 限制禁止应用从数据目录执行二进制**（SELinux `error=13 Permission denied`），jniLibs 是官方认可的执行位置
  - 副作用：无需运行时拷贝（首次启动零成本）、卸载自动清理、天然按 ABI 分发
- 支持 ABI：**arm64-v8a**（全部现代真机）；**x86_64**（模拟器）需 `SKIP_X86_64=0 bash scripts/build-frpc.sh` 额外编译后打入 APK
- **armeabi-v7a 未支持**：Go 对 android/arm 32 位不支持 `CGO_ENABLED=0` 静态链接，上游 frp 官方发布版同样只提供 android/arm64
- 重新编译/升级 frpc：`bash scripts/build-frpc.sh`（自动写入 VERSION 文件供设置页展示）

## 已知限制（v1.0）

- 导入 frpc.toml 时，未建模的高级字段会被丢弃：**插件（plugin）、健康检查（healthCheck）、注解（annotations）、metadatas、featureGates、includes、loadBalancer**。需要这些能力请直接使用 frpc.toml 原文件（可通过其他 frpc 客户端运行）
- 导入时会按代理类型自动清理不支持的字段（如 sudp/tcpmux 的 remotePort）——frp 按类型严格解析，多余字段会导致启动失败
- 界面暂不提供 visitor 之外的 visitor 高级字段编辑（keepTunnelOpen 等已支持）
- Admin API 关闭时无在线状态/监控（仅日志）
- 32 位（armeabi-v7a）设备不可用；x86_64 模拟器需自行用脚本编译引擎后打包
- 界面编辑中途旋转屏幕不会丢失（草稿在 Activity 级 Store 中），但进程意外被杀后未保存草稿会丢失

## 开发说明

- 单例 `FrpcSupervisor` 是运行时中枢：进程启停/看护/日志/状态轮询/资源采样；`FrpcService` 负责前台生命周期与通知；UI 直接收集其 StateFlow
- `shared` 模块测试在 JVM 与 Android 单元测试双端运行；admin 客户端测试基于本地 `HttpServer`
- TOML 兼容：ktoml 0.7.1 对 `[[proxies]]` 内点分键（`transport.x = y`）存在重复子表缺陷，`FrpcToml.mergeDuplicateTables` 已在解析后合并修复，真实 frpc 示例配置解析有测试覆盖

## 许可

应用代码：MIT。frpc 二进制来自 [fatedier/frp](https://github.com/fatedier/frp)（Apache-2.0）。
