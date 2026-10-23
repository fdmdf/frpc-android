package com.frpc.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frpc.android.R
import com.frpc.android.di.AppContainer
import com.frpc.android.ui.components.DropdownField
import com.frpc.android.ui.components.LabeledTextField
import com.frpc.android.ui.components.NumberField
import com.frpc.android.ui.components.SectionCard
import com.frpc.android.ui.components.SwitchRow
import com.frpc.android.ui.components.formFieldKey
import com.frpc.android.ui.components.validationMessage
import com.frpc.android.ui.nav.AppNavigator
import com.frpc.shared.model.AuthConfig
import com.frpc.shared.model.FrpcConfig
import com.frpc.shared.model.ProxyConfig
import com.frpc.shared.model.ProxyTransportConfig
import com.frpc.shared.model.ProxyType
import com.frpc.shared.validate.ConfigValidator
import com.frpc.shared.validate.Severity
import com.frpc.shared.validate.ValidationIssue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyEditScreen(
    container: AppContainer,
    navigator: AppNavigator,
    profileId: String,
    proxyIndex: Int,
) {
    val store = container.configEditorStore
    val proxy by store.proxyDraft.collectAsStateWithLifecycle()
    var issues by remember { mutableStateOf<List<ValidationIssue>>(emptyList()) }

    // 防重入：旋转/重绘时若草稿已属于该下标则跳过初始化（避免丢失未保存输入）
    LaunchedEffect(proxyIndex) {
        if (store.hasProxyDraft(proxyIndex)) return@LaunchedEffect
        val existing = store.draft.value?.config?.proxies?.getOrNull(proxyIndex)
        store.beginProxyEdit(proxyIndex, existing)
    }

    val current = proxy ?: return
    val type = ProxyType.fromId(current.type)
    val errorFields = remember(issues) { issues.map { it.formFieldKey() }.toSet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.proxy_edit)) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        // 复用 shared 层校验器（单代理临时配置），必填项不满足则阻止保存
                        val proxyIssues = ConfigValidator.validate(
                            FrpcConfig(
                                serverAddr = "validation",
                                auth = AuthConfig(token = "validation"),
                                proxies = listOf(current),
                            ),
                        ).filter { it.field.startsWith("proxies[") }
                        val blocking = proxyIssues.filter { it.severity == Severity.ERROR }
                        if (blocking.isNotEmpty()) {
                            issues = blocking
                        } else {
                            store.commitProxy(proxyIndex, current)
                            navigator.pop()
                        }
                    }) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (issues.isNotEmpty()) {
                SectionCard(title = stringResource(R.string.validation_title)) {
                    issues.forEach { issue ->
                        Text(
                            text = "• " + validationMessage(issue),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            SectionCard {
                LabeledTextField(
                    value = current.name,
                    onValueChange = { v -> store.updateProxy { it.copy(name = v) } },
                    label = requiredLabel(stringResource(R.string.field_proxy_name), true),
                    isError = "name" in errorFields,
                )
                DropdownField(
                    label = requiredLabel(stringResource(R.string.field_proxy_type), true),
                    selected = current.type,
                    options = ProxyType.entries.map { it.id },
                    optionLabel = { id -> proxyTypeLabel(id) },
                    onSelect = { v -> store.updateProxy { it.copy(type = v) } },
                )
                SwitchRow(
                    label = stringResource(R.string.field_proxy_enabled),
                    checked = current.enabled ?: true,
                    onCheckedChange = { v -> store.updateProxy { it.copy(enabled = v) } },
                )
            }

            if (type?.needsLocalAddr == true) {
                SectionCard(title = stringResource(R.string.field_local_ip)) {
                    LabeledTextField(
                        value = current.localIP.orEmpty(),
                        onValueChange = { v -> store.updateProxy { it.copy(localIP = v) } },
                        label = requiredLabel(stringResource(R.string.field_local_ip), true),
                        isError = "localIP" in errorFields,
                    )
                    NumberField(
                        value = (current.localPort ?: "").toString(),
                        onValueChange = { v ->
                            store.updateProxy { it.copy(localPort = v.toIntOrNull()) }
                        },
                        label = requiredLabel(stringResource(R.string.field_local_port), true),
                        isError = "localPort" in errorFields,
                    )
                }
            }

            if (type?.needsRemotePort == true) {
                SectionCard {
                    NumberField(
                        value = (current.remotePort ?: "").toString(),
                        onValueChange = { v ->
                            store.updateProxy { it.copy(remotePort = v.toIntOrNull()) }
                        },
                        label = requiredLabel(stringResource(R.string.field_remote_port), true),
                        supportingText = "0 = frps 随机分配",
                        isError = "remotePort" in errorFields,
                    )
                }
            }

            if (type?.domainBased == true) {
                SectionCard {
                    LabeledTextField(
                        value = current.subdomain.orEmpty(),
                        onValueChange = { v ->
                            store.updateProxy { it.copy(subdomain = v.ifBlank { null }) }
                        },
                        label = stringResource(R.string.field_subdomain),
                    )
                    LabeledTextField(
                        value = current.customDomains?.joinToString(",").orEmpty(),
                        onValueChange = { v ->
                            store.updateProxy {
                                it.copy(
                                    customDomains = v.split(",")
                                        .map { d -> d.trim() }
                                        .filter { d -> d.isNotBlank() }
                                        .ifEmpty { null },
                                )
                            }
                        },
                        label = requiredLabel(stringResource(R.string.field_custom_domains), true),
                        supportingText = stringResource(R.string.field_custom_domains) + " / " + stringResource(R.string.field_subdomain),
                        isError = "customDomains" in errorFields,
                    )
                    if (type == ProxyType.HTTP || type == ProxyType.HTTPS) {
                        LabeledTextField(
                            value = current.locations?.joinToString(",").orEmpty(),
                            onValueChange = { v ->
                                store.updateProxy {
                                    it.copy(
                                        locations = v.split(",")
                                            .map { d -> d.trim() }
                                            .filter { d -> d.isNotBlank() }
                                            .ifEmpty { null },
                                    )
                                }
                            },
                            label = stringResource(R.string.field_locations),
                        )
                        LabeledTextField(
                            value = current.httpUser.orEmpty(),
                            onValueChange = { v ->
                                store.updateProxy { it.copy(httpUser = v.ifBlank { null }) }
                            },
                            label = stringResource(R.string.field_http_user),
                        )
                        LabeledTextField(
                            value = current.httpPassword.orEmpty(),
                            onValueChange = { v ->
                                store.updateProxy { it.copy(httpPassword = v.ifBlank { null }) }
                            },
                            label = stringResource(R.string.field_http_password),
                        )
                        LabeledTextField(
                            value = current.hostHeaderRewrite.orEmpty(),
                            onValueChange = { v ->
                                store.updateProxy { it.copy(hostHeaderRewrite = v.ifBlank { null }) }
                            },
                            label = stringResource(R.string.field_host_header_rewrite),
                        )
                    }
                }
            }

            if (type == ProxyType.TCPMUX) {
                SectionCard {
                    LabeledTextField(
                        value = current.multiplexer ?: "httpconnect",
                        onValueChange = { v -> store.updateProxy { it.copy(multiplexer = v) } },
                        label = requiredLabel(stringResource(R.string.field_multiplexer), true),
                        isError = "multiplexer" in errorFields,
                    )
                }
            }

            if (type?.needsSecretKey == true) {
                SectionCard {
                    LabeledTextField(
                        value = current.secretKey.orEmpty(),
                        onValueChange = { v ->
                            store.updateProxy { it.copy(secretKey = v.ifBlank { null }) }
                        },
                        label = requiredLabel(stringResource(R.string.field_secret_key), true),
                        isError = "secretKey" in errorFields,
                    )
                    LabeledTextField(
                        value = current.allowUsers?.joinToString(",").orEmpty(),
                        onValueChange = { v ->
                            store.updateProxy {
                                it.copy(
                                    allowUsers = v.split(",")
                                        .map { u -> u.trim() }
                                        .filter { u -> u.isNotBlank() }
                                        .ifEmpty { null },
                                )
                            }
                        },
                        label = stringResource(R.string.field_allow_users),
                    )
                }
            }

            SectionCard(title = stringResource(R.string.field_bandwidth_limit)) {
                LabeledTextField(
                    value = current.transport?.bandwidthLimit.orEmpty(),
                    onValueChange = { v ->
                        store.updateProxy {
                            it.copy(
                                transport = (it.transport ?: ProxyTransportConfig())
                                    .copy(bandwidthLimit = v.ifBlank { null }),
                            )
                        }
                    },
                    label = stringResource(R.string.field_bandwidth_limit),
                    placeholder = "1MB",
                )
                SwitchRow(
                    label = stringResource(R.string.field_use_encryption),
                    checked = current.transport?.useEncryption ?: false,
                    onCheckedChange = { v ->
                        store.updateProxy {
                            it.copy(
                                transport = (it.transport ?: ProxyTransportConfig()).copy(useEncryption = v),
                            )
                        }
                    },
                )
                SwitchRow(
                    label = stringResource(R.string.field_use_compression),
                    checked = current.transport?.useCompression ?: false,
                    onCheckedChange = { v ->
                        store.updateProxy {
                            it.copy(
                                transport = (it.transport ?: ProxyTransportConfig()).copy(useCompression = v),
                            )
                        }
                    },
                )
            }
        }
    }
}

/** 必填项标签加 * 标记 */
@Composable
private fun requiredLabel(label: String, required: Boolean): String =
    if (required) "$label *" else label

@Composable
fun proxyTypeLabel(id: String): String = when (id) {
    "tcp" -> stringResource(R.string.proxy_type_tcp)
    "udp" -> stringResource(R.string.proxy_type_udp)
    "http" -> stringResource(R.string.proxy_type_http)
    "https" -> stringResource(R.string.proxy_type_https)
    "stcp" -> stringResource(R.string.proxy_type_stcp)
    "sudp" -> stringResource(R.string.proxy_type_sudp)
    "xtcp" -> stringResource(R.string.proxy_type_xtcp)
    "tcpmux" -> stringResource(R.string.proxy_type_tcpmux)
    else -> id
}
