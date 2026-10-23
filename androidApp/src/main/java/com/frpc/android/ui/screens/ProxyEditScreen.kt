package com.frpc.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.frpc.android.ui.nav.AppNavigator
import com.frpc.shared.model.ProxyConfig
import com.frpc.shared.model.ProxyTransportConfig
import com.frpc.shared.model.ProxyType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyEditScreen(
    container: AppContainer,
    navigator: AppNavigator,
    profileId: String,
    proxyIndex: Int,
) {
    val store = container.configEditorStore
    val scope = rememberCoroutineScope()
    val proxy by store.proxyDraft.collectAsStateWithLifecycle()
    val draft by store.draft.collectAsStateWithLifecycle()

    LaunchedEffect(proxyIndex) {
        val existing = draft?.config?.proxies?.getOrNull(proxyIndex)
        store.beginProxyEdit(existing)
    }

    val current = proxy ?: return
    val type = ProxyType.fromId(current.type)

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
                        store.commitProxy(proxyIndex, current)
                        scope.launch { navigator.pop() }
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
            SectionCard {
                LabeledTextField(
                    value = current.name,
                    onValueChange = { v -> store.updateProxy { it.copy(name = v) } },
                    label = stringResource(R.string.field_proxy_name),
                )
                DropdownField(
                    label = stringResource(R.string.field_proxy_type),
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
                        value = current.localIP ?: "127.0.0.1",
                        onValueChange = { v -> store.updateProxy { it.copy(localIP = v) } },
                        label = stringResource(R.string.field_local_ip),
                    )
                    NumberField(
                        value = (current.localPort ?: "").toString(),
                        onValueChange = { v ->
                            store.updateProxy { it.copy(localPort = v.toIntOrNull()) }
                        },
                        label = stringResource(R.string.field_local_port),
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
                        label = stringResource(R.string.field_remote_port),
                        supportingText = "0 = frps 随机分配",
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
                        label = stringResource(R.string.field_custom_domains),
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
                        label = stringResource(R.string.field_multiplexer),
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
                        label = stringResource(R.string.field_secret_key),
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
