package com.frpc.shared.validate

import com.frpc.shared.model.FrpcConfig
import com.frpc.shared.model.ProxyType

/** 问题严重级别 */
enum class Severity { ERROR, WARNING }

/** 问题所属配置区 */
enum class Scope { GLOBAL, AUTH, WEB_SERVER, TRANSPORT, LOG, PROXY, VISITOR }

/** 问题原因（UI 层映射为本地化文案） */
enum class Reason {
    EMPTY_REQUIRED_FIELD,
    PORT_OUT_OF_RANGE,
    PORT_IN_USE,
    INVALID_VALUE,
    DUPLICATE_NAME,
    TYPE_FIELD_MISSING,
    DOMAIN_OR_SUBDOMAIN_REQUIRED,
    UNSUPPORTED_VALUE,
}

data class ValidationIssue(
    val severity: Severity,
    val scope: Scope,
    /** 字段路径，如 "serverAddr" / "proxies[0].remotePort" */
    val field: String,
    val reason: Reason,
    /** 代理/访问者名（PROXY/VISITOR 作用域时非空） */
    val entityName: String? = null,
)

/**
 * frpc 配置校验：在启动前拦截明显错误，对齐 frpc-desktop 的端口冲突预检等能力。
 * portInUse 由调用方注入（真实实现尝试绑定本地端口），保持本类纯函数可测。
 */
object ConfigValidator {
    private const val PORT_MIN = 1
    private const val PORT_MAX = 65535

    fun validate(
        config: FrpcConfig,
        portInUse: (Int) -> Boolean = { false },
    ): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()

        if (config.serverAddr.isBlank()) {
            issues += ValidationIssue(Severity.ERROR, Scope.GLOBAL, "serverAddr", Reason.EMPTY_REQUIRED_FIELD)
        }
        checkPort(config.serverPort, "serverPort", Scope.GLOBAL, issues)

        // auth
        when (config.auth.method) {
            "token" -> if (config.auth.token.isBlank()) {
                issues += ValidationIssue(Severity.ERROR, Scope.AUTH, "auth.token", Reason.EMPTY_REQUIRED_FIELD)
            }
            "oidc" -> issues += ValidationIssue(
                Severity.WARNING, Scope.AUTH, "auth.method", Reason.UNSUPPORTED_VALUE,
            )
            else -> issues += ValidationIssue(
                Severity.ERROR, Scope.AUTH, "auth.method", Reason.INVALID_VALUE,
            )
        }

        // webServer（admin API）
        config.webServer?.let { ws ->
            if (ws.addr.isBlank()) {
                issues += ValidationIssue(Severity.ERROR, Scope.WEB_SERVER, "webServer.addr", Reason.EMPTY_REQUIRED_FIELD)
            }
            checkPort(ws.port, "webServer.port", Scope.WEB_SERVER, issues)
            if (ws.port in PORT_MIN..PORT_MAX && portInUse(ws.port)) {
                issues += ValidationIssue(Severity.ERROR, Scope.WEB_SERVER, "webServer.port", Reason.PORT_IN_USE)
            }
        }

        // transport
        config.transport?.let { t ->
            if (t.protocol !in setOf("tcp", "kcp", "quic", "websocket", "wss")) {
                issues += ValidationIssue(Severity.ERROR, Scope.TRANSPORT, "transport.protocol", Reason.INVALID_VALUE)
            }
            if (t.poolCount < 0) {
                issues += ValidationIssue(Severity.ERROR, Scope.TRANSPORT, "transport.poolCount", Reason.INVALID_VALUE)
            }
            if (t.dialServerTimeout < 0 || t.dialServerKeepalive < 0) {
                issues += ValidationIssue(Severity.ERROR, Scope.TRANSPORT, "transport.dialServerTimeout", Reason.INVALID_VALUE)
            }
        }

        // log
        config.log?.let { l ->
            if (l.level !in setOf("trace", "debug", "info", "warn", "error")) {
                issues += ValidationIssue(Severity.ERROR, Scope.LOG, "log.level", Reason.INVALID_VALUE)
            }
        }

        // proxies
        val names = mutableSetOf<String>()
        config.proxies.forEachIndexed { index, proxy ->
            val prefix = "proxies[$index]"
            if (proxy.name.isBlank()) {
                issues += ValidationIssue(Severity.ERROR, Scope.PROXY, "$prefix.name", Reason.EMPTY_REQUIRED_FIELD)
            } else if (!names.add(proxy.name)) {
                issues += ValidationIssue(
                    Severity.ERROR, Scope.PROXY, "$prefix.name", Reason.DUPLICATE_NAME, proxy.name,
                )
            }
            val type = ProxyType.fromId(proxy.type)
            if (type == null) {
                issues += ValidationIssue(
                    Severity.ERROR, Scope.PROXY, "$prefix.type", Reason.INVALID_VALUE, proxy.name,
                )
                return@forEachIndexed
            }
            if (type.needsLocalAddr) {
                if (proxy.localIP.isNullOrBlank()) {
                    issues += ValidationIssue(
                        Severity.ERROR, Scope.PROXY, "$prefix.localIP", Reason.EMPTY_REQUIRED_FIELD, proxy.name,
                    )
                }
                if (proxy.localPort == null || proxy.localPort !in PORT_MIN..PORT_MAX) {
                    issues += ValidationIssue(
                        Severity.ERROR, Scope.PROXY, "$prefix.localPort", Reason.PORT_OUT_OF_RANGE, proxy.name,
                    )
                }
            }
            if (type.needsRemotePort && (proxy.remotePort == null || proxy.remotePort !in 0..PORT_MAX)) {
                // remotePort = 0 表示由 frps 随机分配，合法
                issues += ValidationIssue(
                    Severity.ERROR, Scope.PROXY, "$prefix.remotePort", Reason.PORT_OUT_OF_RANGE, proxy.name,
                )
            }
            if (type.needsSecretKey && proxy.secretKey.isNullOrBlank()) {
                issues += ValidationIssue(
                    Severity.ERROR, Scope.PROXY, "$prefix.secretKey", Reason.EMPTY_REQUIRED_FIELD, proxy.name,
                )
            }
            if (type.domainBased) {
                val hasDomain = !proxy.customDomains.isNullOrEmpty() || !proxy.subdomain.isNullOrBlank()
                if (!hasDomain) {
                    issues += ValidationIssue(
                        Severity.ERROR, Scope.PROXY, "$prefix.customDomains",
                        Reason.DOMAIN_OR_SUBDOMAIN_REQUIRED, proxy.name,
                    )
                }
            }
            if (type == ProxyType.TCPMUX && proxy.multiplexer.isNullOrBlank()) {
                issues += ValidationIssue(
                    Severity.ERROR, Scope.PROXY, "$prefix.multiplexer", Reason.EMPTY_REQUIRED_FIELD, proxy.name,
                )
            }
        }

        // visitors
        config.visitors.forEachIndexed { index, visitor ->
            val prefix = "visitors[$index]"
            if (visitor.name.isBlank()) {
                issues += ValidationIssue(
                    Severity.ERROR, Scope.VISITOR, "$prefix.name", Reason.EMPTY_REQUIRED_FIELD,
                )
            }
            if (visitor.type !in setOf("stcp", "xtcp")) {
                issues += ValidationIssue(
                    Severity.ERROR, Scope.VISITOR, "$prefix.type", Reason.INVALID_VALUE, visitor.name,
                )
            }
            if (visitor.serverName.isBlank()) {
                issues += ValidationIssue(
                    Severity.ERROR, Scope.VISITOR, "$prefix.serverName", Reason.EMPTY_REQUIRED_FIELD, visitor.name,
                )
            }
        }

        return issues
    }

    fun hasError(issues: List<ValidationIssue>): Boolean =
        issues.any { it.severity == Severity.ERROR }

    private fun checkPort(
        port: Int,
        field: String,
        scope: Scope,
        issues: MutableList<ValidationIssue>,
    ) {
        if (port !in PORT_MIN..PORT_MAX) {
            issues += ValidationIssue(Severity.ERROR, scope, field, Reason.PORT_OUT_OF_RANGE)
        }
    }
}
