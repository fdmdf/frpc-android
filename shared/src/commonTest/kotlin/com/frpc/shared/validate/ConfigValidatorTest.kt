package com.frpc.shared.validate

import com.frpc.shared.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConfigValidatorTest {

    private fun baseConfig() = FrpcConfig(
        serverAddr = "frps.example.com",
        auth = AuthConfig(token = "tok"),
    )

    @Test
    fun validConfig_noIssues() {
        val config = baseConfig().copy(
            webServer = WebServerConfig(port = 7400),
            proxies = listOf(
                ProxyConfig(name = "ssh", type = "tcp", localIP = "127.0.0.1", localPort = 22, remotePort = 6001),
                ProxyConfig(name = "web", type = "http", localIP = "127.0.0.1", localPort = 80, subdomain = "web"),
                ProxyConfig(name = "st", type = "stcp", localIP = "127.0.0.1", localPort = 22, secretKey = "k"),
            ),
        )
        val issues = ConfigValidator.validate(config, portInUse = { false })
        assertTrue(issues.isEmpty(), "unexpected issues: $issues")
    }

    @Test
    fun missingServerAddrAndToken_errors() {
        val issues = ConfigValidator.validate(
            FrpcConfig(serverAddr = "", auth = AuthConfig(token = "")),
        )
        assertTrue(issues.any { it.scope == Scope.GLOBAL && it.reason == Reason.EMPTY_REQUIRED_FIELD })
        assertTrue(issues.any { it.scope == Scope.AUTH && it.reason == Reason.EMPTY_REQUIRED_FIELD })
        assertTrue(ConfigValidator.hasError(issues))
    }

    @Test
    fun webServerPortInUse_error() {
        val issues = ConfigValidator.validate(
            baseConfig().copy(webServer = WebServerConfig(port = 7400)),
            portInUse = { it == 7400 },
        )
        assertTrue(issues.any { it.reason == Reason.PORT_IN_USE && it.field == "webServer.port" })
    }

    @Test
    fun duplicateProxyName_error() {
        val issues = ConfigValidator.validate(
            baseConfig().copy(
                proxies = listOf(
                    ProxyConfig(name = "ssh", type = "tcp", localIP = "127.0.0.1", localPort = 22, remotePort = 6001),
                    ProxyConfig(name = "ssh", type = "tcp", localIP = "127.0.0.1", localPort = 22, remotePort = 6002),
                ),
            ),
        )
        assertTrue(issues.any { it.reason == Reason.DUPLICATE_NAME && it.entityName == "ssh" })
    }

    @Test
    fun httpProxyWithoutDomain_error() {
        val issues = ConfigValidator.validate(
            baseConfig().copy(
                proxies = listOf(
                    ProxyConfig(name = "web", type = "http", localIP = "127.0.0.1", localPort = 80),
                ),
            ),
        )
        assertTrue(issues.any { it.reason == Reason.DOMAIN_OR_SUBDOMAIN_REQUIRED })
    }

    @Test
    fun remotePortZero_isAllowed_forRandomAssignment() {
        val issues = ConfigValidator.validate(
            baseConfig().copy(
                proxies = listOf(
                    ProxyConfig(name = "ssh", type = "tcp", localIP = "127.0.0.1", localPort = 22, remotePort = 0),
                ),
            ),
        )
        assertTrue(issues.none { it.field.contains("remotePort") })
    }

    @Test
    fun stcpWithoutSecretKey_error() {
        val issues = ConfigValidator.validate(
            baseConfig().copy(
                proxies = listOf(
                    ProxyConfig(name = "st", type = "stcp", localIP = "127.0.0.1", localPort = 22),
                ),
            ),
        )
        assertTrue(issues.any { it.reason == Reason.EMPTY_REQUIRED_FIELD && it.field.endsWith("secretKey") })
    }

    @Test
    fun invalidProxyType_error() {
        val issues = ConfigValidator.validate(
            baseConfig().copy(
                proxies = listOf(ProxyConfig(name = "x", type = "magic")),
            ),
        )
        assertTrue(issues.any { it.field.endsWith(".type") && it.reason == Reason.INVALID_VALUE })
    }

    @Test
    fun portOutOfRange_error() {
        val issues = ConfigValidator.validate(
            baseConfig().copy(
                proxies = listOf(
                    ProxyConfig(name = "ssh", type = "tcp", localIP = "127.0.0.1", localPort = 99999, remotePort = 6001),
                ),
            ),
        )
        assertEquals(1, issues.count { it.reason == Reason.PORT_OUT_OF_RANGE })
    }
}
