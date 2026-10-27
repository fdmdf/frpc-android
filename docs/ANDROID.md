# Android 适配

本文记录 Frpc-Desktop 的 Android 端实现方案，并对照参考项目
[fdmdf/frpc-android](https://github.com/fdmdf/frpc-android) 做了取舍分析。

## 一、参考项目 frpc-android 的关键实现

技术栈：Kotlin + Jetpack Compose + Kotlin Multiplatform，`minSdk 26`，纯原生无 WebView，
内置 frpc v0.71.1（arm64-v8a），Release APK 约 7.5MB。

### 1. 引擎分发：jniLibs + nativeLibraryDir

Android 10 起应用数据目录禁止 `execve`（W^X 限制）。参考项目把 frpc 以
`jniLibs/arm64-v8a/libfrpc.so` 打进 APK，安装时由系统解压到 `nativeLibraryDir`，
该目录允许执行。配套要求：

- 文件名必须匹配 `lib*.so`；
- `android:extractNativeLibs="true"`，且 Gradle `jniLibs.useLegacyPackaging = true`；
- 不支持在线下载/替换引擎，只内置一个版本。

`FrpcBinaryManager` 只做三件事：定位 `nativeLibraryDir/libfrpc.so`、校验
`exists() && canExecute()`、给出可操作的重装提示。

### 2. 运行时中枢：FrpcSupervisor 单例

进程、日志、状态全部收敛到一个进程级单例，UI 和 Service 只读它的 Flow：

- 启动/停止/重载：`ProcessBuilder(binary, "-c", toml)`，`redirectErrorStream(true)`；
- 看护：`waitFor()` 返回后若为「非主动停止」，按 `2s → 5s → 10s → 30s → 60s`
  指数退避重启，最多 5 次，超过则进入 ERROR 并停止轮询；
- 日志：3000 行环形缓冲 + 级别/来源解析 + 访问日志过滤；
- 状态：每 4s 轮询 admin API `/api/status`；每 2s 读 `/proc/<pid>/stat` 采样
  CPU/内存；PID 通过扫描 `/proc` 找「父进程是本进程且 comm 为 frpc/libfrpc」的子进程；
- 配置热重载：admin API 可用时 `PUT /api/reload`（先把最新 TOML 写回磁盘），
  否则整进程重启。

### 3. 前台服务：只负责「占住进程」

`FrpcService` 是 `specialUse` 类型前台服务，本身不做业务：

- `ACTION_START / STOP / RELOAD` 三个入口，转发给 Supervisor；
- 常驻通知展示服务器、在线代理数、运行时长、CPU、内存，5s 刷新，带「停止」action；
- 状态回到 IDLE 时 `stopForeground(REMOVE)` + `stopSelf()` 自退。

### 4. 代理状态「双源」

| 来源 | 优点 | 缺点 |
| --- | --- | --- |
| admin API `/api/status` | 精确的在线状态、实际分配端口、错误原因 | 需开启 `webServer`，只监听回环 |
| 日志推断（`proxy added success` / `start proxy success` / `start error`） | 无需配置 | 拿不到实际端口，控制连接断开时状态要清空 |

先取接口数据，缺失时用日志推断，两者都没有时显示「未知」而不是「离线」。

### 5. 系统集成

- 开机自启：`BOOT_COMPLETED` / `QUICKBOOT_POWERON` / `MY_PACKAGE_REPLACED` 广播直接
  `startForegroundService`（其进程管理在 Kotlin，不依赖 Activity）；
- 电池优化：`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` +
  `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 引导页；
- 通知权限：Android 13+ 动态申请 `POST_NOTIFICATIONS`。

### 6. 配置层

- 多服务器档案，JSON 原子写（`tmp` + `rename`）；
- Activity 级编辑草稿（`ConfigEditorStore`），防旋转丢稿；
- TOML 编解码两个坑：ktoml 对 `[[proxies]]` 内点分键生成重复子表需要合并；
  frp 按代理类型严格解码，导入时要按类型清理非法字段（如 sudp 带 `remotePort`）。

## 二、当前项目的 Android 实现

### 已完成

| 能力 | 实现位置 |
| --- | --- |
| Android 工程 | `src-tauri/gen/android`（`tauri android init` 生成） |
| 引擎分发 | `jniLibs/{arm64-v8a,armeabi-v7a,x86_64}/libfrpc.so` + `extractNativeLibs` + `useLegacyPackaging` |
| 引擎定位 | `src-tauri/src/android.rs`：Kotlin 返回 `nativeLibraryDir`，拼 `libfrpc.so` |
| 执行链路 | `src-tauri/src/process/mod.rs`：Android 分支先 `chmod 755`、拉起前台服务再 `spawn` |
| 前台服务 | `FrpcForegroundService.kt`：`specialUse` + 常驻通知 + `PARTIAL_WAKE_LOCK` |
| 自启桥接 | `FrpcServicePlugin.kt`：`getAppInfo` / `startService` / `stopService` / `updateNotification` / `openUrl` |
| 桌面 API 隔离 | `Cargo.toml` 按目标拆分依赖；`lib.rs` 中托盘、autostart、窗口隐藏、macOS Dock 全部 `#[cfg(desktop)]` |
| 数据目录 | 桌面沿用 `<config>/Frpc-Desktop`，Android 用 `app_data_dir()` |
| 文件选择器 | `src-tauri/src/dialogs.rs`：桌面 rfd，移动端退化为「取消」 |
| 崩溃自动重启 | `process/mod.rs`：`2/5/10/30/60s` 退避，最多 5 次 |
| 移动端 UI | `src/styles/mobile.scss`：底部导航、单列表单、全屏弹窗、安全区 |
| 端内降级 | 隐藏「版本下载」页、自定义 frpc 路径、开机自启/静默启动、导入导出/打开数据目录 |

### 与参考项目的差距

| # | 差距 | 影响 |
| --- | --- | --- |
| 1 | 进程归属仍在 Rust（随 Activity 启动），Service 只做保活 | Activity 被系统回收后 Rust 运行时消失，frpc 也会被带走；无法像参考项目那样纯 Service 拉起 |
| 2 | 无 admin API 状态轮询 | 只能靠日志 grep 判断连接错误，拿不到每个代理的在线状态/实际端口 |
| 3 | 通知内容静态 | 只有「已连接/已断开」，无服务器、在线数、运行时长、CPU/内存，也没有停止按钮 |
| 4 | 无开机自启 | `BOOT_COMPLETED` 起 FGS 无法拉起 Rust 运行时，需架构调整（见 P2） |
| 5 | 无电池优化引导 | 国产 ROM 后台清理会让 frpc 静默掉线 |
| 6 | 日志无内存环形缓冲 | 每次读盘尾部 500 行，无级别解析/过滤 |
| 7 | 无配置热重载 | 改配置要断线重连 |
| 8 | 无移动端测试 | Rust/前端都没有覆盖 Android 分支 |

## 三、改进方案

### P1（本轮已实现）

1. **崩溃自动重启**：`process/mod.rs` 新增 `stopping` / `restart_count` 原子状态，
   看护任务在非主动退出时退避重启，界面收到「X 秒后重连（第 n/5 次）」提示。
2. **移动端布局**：底部导航替代左侧竖排菜单，`el-col` 强制单列，弹窗近全屏，
   触摸目标 ≥ 36px，`env(safe-area-inset-bottom)` 适配手势条。
3. **端内能力降级**：`isAndroidApp` 统一判定，隐藏不可用入口；`frpcVersion`
   校验在 Android 跳过，避免「请配置路径或版本」卡死保存。

### P2（建议下一步）

4. **代理状态接入 admin API**
   - 文件：`src-tauri/src/frpc_admin.rs`（新增）、`commands/mod.rs`、`models.rs`、
     `src/store/frpcDesktop.ts`、`src/views/proxy/index.vue`。
   - 逻辑：frpc 运行且 `webServer.enable` 时，每 4s 请求
     `http://127.0.0.1:<port>/api/status`（带 Basic Auth），把
     `{name, status, remoteAddr, err}` 通过 `frpcProxy:watchStatus` 事件推给前端；
     前端在代理卡片上叠加「在线/异常」徽标；接口不可用时回退到日志推断。
5. **通知摘要 + 停止按钮**
   - 文件：`FrpcForegroundService.kt`、`FrpcServicePlugin.kt`、`process/mod.rs`。
   - 逻辑：Rust 每次状态变化把 `{server, online, total, startedAt}` 传给 Kotlin；
     Kotlin 组装多行通知并 5s 刷新；「停止」action 通过静态回调解到插件，
     再回调 Rust 的 terminate（需要把 `Plugin` 单例暴露给 Service）。
6. **电池优化引导**
   - 文件：`FrpcServicePlugin.kt`（`isIgnoringBatteryOptimizations` /
     `requestIgnoreBatteryOptimizations`）、`config/SystemConfig.vue`。
   - 逻辑：Android 上在系统设置区展示开关，未豁免时点击跳
     `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`。

### P3（架构级）

7. **进程管理下沉 Kotlin**（对齐 `FrpcSupervisor`）
   - Rust 只保留配置生成与数据库；`ProcessManager` 在 Android 上改为调用
     `FrpcRunner`（Kotlin 单例）的 `start(configPath)` / `stop()` / `status()`。
   - 收益：Activity 被杀不影响 frpc；`BOOT_COMPLETED` 可直接拉起；通知与
     `/proc` 采样都在 Kotlin 侧完成。
   - 代价：状态回传需要 Rust 轮询或 Kotlin `trigger` 事件，改动面较大，需要
     真机回归所有 IPC。

## 四、构建

前置：JDK 17、Android SDK（compileSdk 37）、NDK 27、Go 1.26+（frp 0.71 起要求）、
Node 22（用于构建 frpc 自带仪表盘）、Rust Android target。

```sh
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
node scripts/build-frpc-android.mjs          # 生成 jniLibs/*/libfrpc.so
# 默认编译 frp v0.71.1；换版本：FRP_VERSION=v0.71.0 node scripts/build-frpc-android.mjs
# frp 0.62+ 的 frpc 内置 Web 仪表盘，脚本会顺带 npm 构建它；
# 不想编（或环境没有 npm）时加 FRP_WEB=0，会以 -tags noweb 编译，只是仪表盘不可用。
export ANDROID_HOME=$HOME/AppData/Local/Android/Sdk
export NDK_HOME=$ANDROID_HOME/ndk/27.2.12479018
npm run tauri -- android build --apk --target aarch64
```

调试：

```sh
npm run tauri:android:dev
```

### Windows 构建注意

`tauri android build` 会把 Rust 产物软链到 `jniLibs/<abi>/`，Windows 上创建符号链接
需要「开发者模式」（设置 → 系统 → 开发者选项）。未开启时会报
`Creation symbolic link is not allowed for this system`。

临时绕过的做法：先自行编译并复制产物，再让 Gradle 跳过 Rust 任务。

```sh
# ⚠️ 必须带 --features custom-protocol，否则 WebView 会去连 devUrl（localhost:3344）
cargo build --features custom-protocol --target aarch64-linux-android
cp target/aarch64-linux-android/debug/libfrpc_desktop_lib.so \
   gen/android/app/src/main/jniLibs/arm64-v8a/
cd gen/android && ./gradlew assembleDebug -x rustBuildArm64Debug
```

原因：`tauri::is_dev()` 的定义是 `!cfg!(feature = "custom-protocol")`。开启时走
`https://tauri.localhost` + 随包编译进去的前端资源；没开启就认为是开发模式，
窗口 URL 直接取 `build.devUrl`（本机 3344 端口），手机上必然连不上。
CLI 的 `tauri build` / `tauri android build` 会自动加这个 feature，手工 cargo build 不会。

产物在 `src-tauri/gen/android/app/build/outputs/apk/<abi>/debug/app-<abi>-debug.apk`
（Gradle 按 ABI 拆包，arm64 真机取 `arm64/debug/app-arm64-debug.apk`）。

前端产物由 `app/build.gradle.kts` 里的 `copyFrontendAssets` 任务在 `preBuild` 前
自动从仓库根目录的 `dist/` 拷进 `app/src/main/assets`（当前版本实际由 Rust 侧编译期
嵌入的资源提供，这份拷贝作为兜底保留）。

> 打包前务必先跑 `npm run build`：前端资源是编译期嵌进 Rust 库的，
> `dist/` 没更新，APK 里就是旧界面。

### 应用图标

`tauri android init` 生成的是 **Android Studio 默认模板**（绿色底 + 机器人），
并不会自动套用 `src-tauri/icons/icon.png`。所以安卓图标需要单独生成：

```sh
python scripts/gen-android-icons.py                 # 默认取 src-tauri/icons/icon.png
python scripts/gen-android-icons.py path/to/new.png # 换图标时指定新源图
```

脚本会生成各密度的 `ic_launcher.png` / `ic_launcher_round.png`（圆形）/
`ic_launcher_foreground.png`（adaptive 前景），并同步一份到 `src-tauri/icons/android/`。

Android 8+ 走 `mipmap-anydpi-v26/ic_launcher.xml` 的 adaptive icon：
背景层是 `res/drawable/ic_launcher_background.xml`（白色，与 PC 端图标底色一致），
前景层是项目图标。这样无论启动器裁成方形还是圆形，看起来的效果都和 PC 端一致。

### 正式签名

`app/build.gradle.kts` 的 release 变体从环境变量读取签名配置，配好就自动签名，
不配则产出未签名包（无法安装）：

```sh
export KEYSTORE_PATH=/path/to/frpc-desktop.jks
export KEYSTORE_PASSWORD=***
export KEY_ALIAS=frpc-desktop
export KEY_PASSWORD=***

npm run android:apk -- --release     # 产物 app/build/outputs/apk/arm64/release/app-arm64-release.apk
```

release 变体里显式关闭了 `isMinifyEnabled` 与 `isShrinkResources`，**不要打开**：
R8 会重命名 Kotlin 插件类，而 Rust 侧靠 `Class.forName` 反射加载
`priv.liujiawei.frpc.desktop.FrpcServicePlugin`，混淆后启动即崩溃。

> 当前密钥放在仓库外：`C:/Users/Liao/.android-keystores/frpc-desktop.jks`。
> 更换签名密钥会导致已安装用户无法覆盖升级，务必备份。

### 发布到私有仓库 Release

```sh
GITHUB_TOKEN=<PAT> GITHUB_REPO=owner/repo \
  npm run release:github -- --tag v2.0.0 \
    --file <apk 路径> --rename frpc-desktop-2.0.0-android-arm64.apk \
    --notes-file notes.md
```

- token 只从环境变量读取，不会写进任何仓库文件；需要 `repo` 权限
- 同名 tag 已存在时复用该 Release，同名产物先删后传，可重复执行
- tag 不存在时会在仓库默认分支上自动创建
- 本机已登录 Git Credential Manager 时，可免手工填 token：
  `GITHUB_TOKEN=$(printf "protocol=https\nhost=github.com\n\n" | git credential fill | sed -n 's/^password=//p')`
- 自建 GitHub Enterprise 用 `GITHUB_API=https://ghe.example.com/api/v3`

## 五、启动崩溃排查（已修复的两个坑）

### 1. 插件标识符必须是 Java 包名 → 启动即崩溃

`register_android_plugin(identifier, class)` 内部拼成 `{identifier}.{className}` 后走
`Class.forName`（见 `wry::find_class` → `WryActivity.getAppClass`）。标识符写成
`frpc-android` 时，实际查找的是
`frpc-android.priv.liujiawei.frpc.desktop.FrpcServicePlugin`，必然
`ClassNotFoundException`；错误经 `setup` 冒泡到 `.expect("error while running tauri application")`
直接 panic，表现就是打开应用瞬间闪退。

正确写法（与官方插件一致，`tauri-plugin-dialog` 用的是 `app.tauri` + `DialogPlugin`）：

```rust
const PLUGIN_NAME: &str = "priv.liujiawei.frpc.desktop"; // Kotlin 包名
const PLUGIN_CLASS: &str = "FrpcServicePlugin";          // 简单类名
```

同时 `android::init()` 已把注册失败降级为 `log::error!`，即使 Kotlin 侧出问题也不再拖垮启动。

### 2. 主窗口 `visible: false` → 移动端白屏

`tauri.conf.json` 里主窗口不可见，桌面端在 `setup` 中按需 `show()`，移动端原本没有对应逻辑。
现已补上 `#[cfg(mobile)]` 分支，setup 阶段无条件显示主窗口。

## 六、已知限制

- **用途边界**：手机上的 `localIP:localPort` 指手机自身。想穿透电脑上的服务，
  必须填电脑的局域网 IP，或改用「手机自身服务穿透」。
- **开机自启不可用**：受 Tauri 架构限制，需完成 P3 后才能实现。
- **ABI**：`armeabi-v7a` / `x86_64` 使用 `GOOS=linux` 静态二进制（Go 对
  `android/arm`、`android/amd64` 要求 cgo 外部链接），真机以 arm64 为主，
  其余两种仅用于兼容与模拟器。
- **引擎版本固定**：不支持在线切换 frpc 版本；升级需重新打包。
