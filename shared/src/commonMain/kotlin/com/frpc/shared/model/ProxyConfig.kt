package com.frpc.shared.model

import kotlinx.serialization.Serializable

/** frpc 代理类型，与 frp 服务端配置一一对应 */
enum class ProxyType(val id: String) {
    TCP("tcp"),
    UDP("udp"),
    HTTP("http"),
    HTTPS("https"),
    STCP("stcp"),
    SUDP("sudp"),
    XTCP("xtcp"),
    TCPMUX("tcpmux"),
    ;

    /** 是否需要 remotePort（frps 监听端口） */
    val needsRemotePort: Boolean
        get() = this == TCP || this == UDP || this == TCPMUX

    /** 是否需要本地服务地址 */
    val needsLocalAddr: Boolean
        get() = this != TCPMUX

    /** 是否需要 secretKey */
    val needsSecretKey: Boolean
        get() = this == STCP || this == SUDP || this == XTCP

    /** 是否基于域名访问（http/https） */
    val domainBased: Boolean
        get() = this == HTTP || this == HTTPS || this == TCPMUX

    companion object {
        fun fromId(id: String): ProxyType? = entries.firstOrNull { it.id == id }
    }
}

/**
 * 单个代理规则（对应 [[proxies]]）。
 * 可选字段为 null 时不写入 frpc.toml，由 frpc 使用自身默认值。
 */
@Serializable
data class ProxyConfig(
    val name: String,
    val type: String,
    /** false 时生成 enabled = false 禁用该代理 */
    val enabled: Boolean? = null,
    val localIP: String? = null,
    val localPort: Int? = null,
    // ---- tcp / udp / tcpmux ----
    val remotePort: Int? = null,
    // ---- http / https / tcpmux ----
    val subdomain: String? = null,
    val customDomains: List<String>? = null,
    val locations: List<String>? = null,
    val httpUser: String? = null,
    val httpPassword: String? = null,
    val hostHeaderRewrite: String? = null,
    val routeByHTTPUser: String? = null,
    // ---- tcpmux ----
    val multiplexer: String? = null,
    // ---- stcp / sudp / xtcp ----
    val secretKey: String? = null,
    val allowUsers: List<String>? = null,
    // ---- 每代理 transport 子表 ----
    val transport: ProxyTransportConfig? = null,
) {
    val proxyType: ProxyType? get() = ProxyType.fromId(type)
}

@Serializable
data class ProxyTransportConfig(
    /** 例如 "1MB" / "500KB" */
    val bandwidthLimit: String? = null,
    val bandwidthLimitMode: String? = null,
    val useEncryption: Boolean? = null,
    val useCompression: Boolean? = null,
    /** v1 / v2，https 类型代理转发连接信息给本地服务 */
    val proxyProtocolVersion: String? = null,
)

/**
 * 访问者规则（对应 [[visitors]]），用于 stcp/xtcp 场景。
 */
@Serializable
data class VisitorConfig(
    val name: String,
    /** stcp / xtcp */
    val type: String,
    /** 要访问的服务端代理名 */
    val serverName: String,
    val secretKey: String? = null,
    val bindAddr: String = "127.0.0.1",
    /** < 0 表示不监听端口 */
    val bindPort: Int = 0,
    val serverUser: String? = null,
    val keepTunnelOpen: Boolean? = null,
    val fallbackTo: String? = null,
)
