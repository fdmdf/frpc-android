# HANDOFF — frpc 安卓客户端

## 【主线状态】

- **节点**：v1.0 功能开发完成，全量验证通过（单测 + 双构建 + frpc 真实二进制 E2E）。
- **下一步**（按优先级）：
  1. 真机/模拟器安装 `androidApp-debug.apk` 实测 UI 流程（本沙箱无安卓运行时，未做设备级验证）
  2. 视反馈补 visitor 高级字段 / 插件配置导入（v1.1 候选）
  3. 如需上架：配置 release 签名；specialUse FGS 需在 Play 后台说明用途

## 【本 session】

### 目标（用户原话）
「实现一个frpc安卓客户端，可以参考 https://github.com/luckjiawei/frpc-desktop 的界面功能来实现，技术栈要求安装包小、运行快、跨平台。」

### 关键决定
1. **Kotlin Multiplatform（shared: android+jvm）+ Jetpack Compose M3**：原生编译满足「小+快」；逻辑层双端复用满足「跨平台」且有双端测试证据
2. **frpc 引擎随包分发**：frp v0.71.1 源码 Go 交叉编译，arm64-v8a（静态 16.9MB，默认打包）+ x86_64（NDK cgo，仅模拟器，`SKIP_X86_64=0` 启用）；armeabi-v7a 不支持（Go 限制，上游同样只出 arm64）
3. **版本矩阵**（沙箱内反复验证）：AGP 8.13.2 + Gradle 8.13 + Kotlin 2.4.21 + compose-bom 2026.06.00 + core-ktx 1.18.0 + lifecycle 2.10.0。AGP 9.x 路线因内置 Kotlin/KMP DSL 巨变弃用
4. **admin API 状态监控**（对齐 frpc-desktop 功能）：webServer 段可开关；关闭时仅日志模式
5. **loginFailExit 默认 false**（E2E 实证发现）：手机网络切换应持续重连
6. 不引 Room/KSP/Navigation/DI 框架：JSON 文件持久化 + 自写返回栈 + Activity 级编辑草稿 Store

### 状态
- ✅ shared 模块 26+5 测试全绿（JVM + 安卓单测双端）：TOML 往返、真实 frpc 示例解析、admin API 集成（本地 HttpServer）、校验器、日志解析（真实 frpc 日志行）
- ✅ Debug APK 24.9MB / Release APK 7.4MB（未签名），均含 arm64 frpc
- ✅ E2E（Windows frpc 真实二进制）：生成 TOML → frpc 解析启动 → /healthz 200 → /api/status 200/401 → 持续重连保活
- ⚠️ 未做设备级运行验证（沙箱无安卓运行时/模拟器）
- ⚠️ Release 构建时 D8 对 Kotlin 2.4 元数据有提示性日志（不影响构建；序列化插件已生成直接引用）

### 环境备忘（沙箱）
- Gradle 必须显式传 `-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=27103`（及 https 同）；curl 走环境变量自动代理
- github.com 被代理 502 拦截；frp 源码走 goproxy.cn module zip
- 工具链：`~/.workbuddy/binaries/{jdk21.0.13_0,go1.27.3,gradle-8.13}`；frp 源码在 `~/.workbuddy/src/frp-src/`
- MSYS 路径陷阱：bash 里 `|` 传给 .bat 会被拆参；Python/JVM 不认 `/c/` 路径

### 交接件索引
- 构建/运行：README.md
- frpc 复编：scripts/build-frpc.sh
- 当日日志：.workbuddy/memory/2026-10-23.md
