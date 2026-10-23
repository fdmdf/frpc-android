package com.frpc.shared.model

import kotlinx.serialization.Serializable

/**
 * frpc 全局配置（对应 frpc.toml 顶层字段与各 section）。
 * 可空 section 为 null 时不写入 frpc.toml，由 frpc 使用自身默认值：
 * - webServer = null  → 不启动 admin API（端口 0 不监听，见 frp client/service.go）
 * - log/transport = null → 使用 frpc 默认值（console/info、tcp+tls）
 */
@Serializable
data class FrpcConfig(
    val serverAddr: String = "",
    val serverPort: Int = 7000,
    /** 代理名前缀，最终名为 {user}.{proxy}，为空则不加前缀 */
    val user: String = "",
    val clientID: String = "",
    /**
     * 首次登录失败是否退出。
     * 默认 false：手机网络频繁切换，应让 frpc 持续重连而非退出（桌面端 frpc 默认为 true）。
     */
    val loginFailExit: Boolean = false,
    val log: LogConfig? = null,
    val auth: AuthConfig = AuthConfig(),
    /**
     * Admin API（webServer）默认开启：App 的在线状态/资源监控依赖它。
     * 仅监听 127.0.0.1，不对外暴露；置为 null 可关闭。
     */
    val webServer: WebServerConfig? = WebServerConfig(),
    val transport: TransportConfig? = null,
    val udpPacketSize: Int? = null,
    val proxies: List<ProxyConfig> = emptyList(),
    val visitors: List<VisitorConfig> = emptyList(),
)

@Serializable
data class LogConfig(
    /** console 或真实日志文件路径；安卓端固定使用 console，由 App 采集 stdout */
    val to: String = "console",
    /** trace/debug/info/warn/error */
    val level: String = "info",
    val maxDays: Int = 3,
)

@Serializable
data class AuthConfig(
    /** token 或 oidc */
    val method: String = "token",
    val token: String = "",
)

@Serializable
data class WebServerConfig(
    /** admin API 监听地址，安卓端仅本机访问 */
    val addr: String = "127.0.0.1",
    val port: Int = 7400,
    val user: String = "admin",
    val password: String = "admin",
    val pprofEnable: Boolean = false,
)

@Serializable
data class TransportConfig(
    /** tcp/kcp/quic/websocket/wss */
    val protocol: String = "tcp",
    val tcpMux: Boolean = true,
    val poolCount: Int = 0,
    val tls: TlsConfig? = null,
    val dialServerTimeout: Int = 10,
    val dialServerKeepalive: Int = 7200,
    /** 连接 frps 时使用的本地 IP，为空则不绑定 */
    val connectServerLocalIP: String? = null,
    /** http/https/socks5/socks5h 代理，仅 protocol=tcp 有效 */
    val proxyURL: String? = null,
)

@Serializable
data class TlsConfig(
    /** frp >= v0.50.0 默认开启 TLS */
    val enable: Boolean = true,
    val serverName: String? = null,
)
