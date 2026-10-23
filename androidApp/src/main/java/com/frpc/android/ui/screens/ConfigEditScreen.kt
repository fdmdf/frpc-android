package com.frpc.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frpc.android.R
import com.frpc.android.data.ServerProfile
import com.frpc.android.di.AppContainer
import com.frpc.android.ui.components.DropdownField
import com.frpc.android.ui.components.LabeledTextField
import com.frpc.android.ui.components.NumberField
import com.frpc.android.ui.components.SectionCard
import com.frpc.android.ui.components.SwitchRow
import com.frpc.android.ui.components.fieldLabelRes
import com.frpc.android.ui.components.reasonTemplateRes
import com.frpc.android.ui.nav.AppNavigator
import com.frpc.android.ui.nav.Screen
import com.frpc.android.util.isPortInUse
import com.frpc.shared.model.FrpcConfig
import com.frpc.shared.model.LogConfig
import com.frpc.shared.model.ProxyType
import com.frpc.shared.model.TransportConfig
import com.frpc.shared.model.WebServerConfig
import com.frpc.shared.validate.ValidationIssue
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigEditScreen(
    container: AppContainer,
    navigator: AppNavigator,
    profileId: String?,
) {
    val store = container.configEditorStore
    val scope = rememberCoroutineScope()
    val draft by store.draft.collectAsStateWithLifecycle()
    var showValidation by remember { mutableStateOf<List<ValidationIssue>?>(null) }

    LaunchedEffect(profileId) {
        if (profileId == null) {
            store.beginNew()
        } else {
            container.configRepository.find(profileId)?.let { store.beginEdit(it) }
        }
    }

    val current = draft ?: return
    val config = current.config

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (current.isNew) R.string.config_new else R.string.config_edit,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        val issues = store.validate(::isPortInUse)
                        if (issues.any { it.severity == com.frpc.shared.validate.Severity.ERROR }) {
                            showValidation = issues
                        } else {
                            val profile = store.toProfile() ?: return@TextButton
                            scope.launch {
                                container.configRepository.upsert(profile)
                                if (container.settingsRepository.defaultProfileId.value == null) {
                                    container.settingsRepository.setDefaultProfileId(profile.id)
                                }
                                store.clear()
                                navigator.pop()
                            }
                        }
                    }) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(title = stringResource(R.string.field_server_addr)) {
                    LabeledTextField(
                        value = current.name,
                        onValueChange = { store.rename(it) },
                        label = stringResource(R.string.config_name),
                    )
                    LabeledTextField(
                        value = config.serverAddr,
                        onValueChange = { v -> store.update { it.copy(serverAddr = v) } },
                        label = stringResource(R.string.field_server_addr),
                        placeholder = "frps.example.com",
                    )
                    NumberField(
                        value = config.serverPort.toString(),
                        onValueChange = { v -> store.update { it.copy(serverPort = v.toIntOrNull() ?: 7000) } },
                        label = stringResource(R.string.field_server_port),
                    )
                    LabeledTextField(
                        value = config.user,
                        onValueChange = { v -> store.update { it.copy(user = v) } },
                        label = stringResource(R.string.field_user),
                        placeholder = "留空 = 不加前缀",
                    )
                    LabeledTextField(
                        value = config.clientID,
                        onValueChange = { v -> store.update { it.copy(clientID = v) } },
                        label = stringResource(R.string.field_client_id),
                    )
                    SwitchRow(
                        label = stringResource(R.string.field_login_fail_exit),
                        checked = config.loginFailExit,
                        onCheckedChange = { v -> store.update { it.copy(loginFailExit = v) } },
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.field_auth_method)) {
                    DropdownField(
                        label = stringResource(R.string.field_auth_method),
                        selected = config.auth.method,
                        options = listOf("token", "oidc"),
                        optionLabel = { it },
                        onSelect = { v -> store.update { it.copy(auth = it.auth.copy(method = v)) } },
                    )
                    if (config.auth.method == "token") {
                        LabeledTextField(
                            value = config.auth.token,
                            onValueChange = { v -> store.update { it.copy(auth = it.auth.copy(token = v)) } },
                            label = stringResource(R.string.field_auth_token),
                        )
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.field_admin_enabled)) {
                    SwitchRow(
                        label = stringResource(R.string.field_admin_enabled),
                        checked = config.webServer != null,
                        onCheckedChange = { enabled ->
                            store.update {
                                it.copy(
                                    webServer = if (enabled) {
                                        it.webServer ?: WebServerConfig()
                                    } else {
                                        null
                                    },
                                )
                            }
                        },
                        supportingText = stringResource(R.string.home_admin_off),
                    )
                    config.webServer?.let { ws ->
                        LabeledTextField(
                            value = ws.addr,
                            onValueChange = { v -> store.update { it.copy(webServer = ws.copy(addr = v)) } },
                            label = stringResource(R.string.field_admin_addr),
                        )
                        NumberField(
                            value = ws.port.toString(),
                            onValueChange = { v ->
                                store.update { it.copy(webServer = ws.copy(port = v.toIntOrNull() ?: 7400)) }
                            },
                            label = stringResource(R.string.field_admin_port),
                        )
                        LabeledTextField(
                            value = ws.user,
                            onValueChange = { v -> store.update { it.copy(webServer = ws.copy(user = v)) } },
                            label = stringResource(R.string.field_admin_user),
                        )
                        LabeledTextField(
                            value = ws.password,
                            onValueChange = { v -> store.update { it.copy(webServer = ws.copy(password = v)) } },
                            label = stringResource(R.string.field_admin_password),
                        )
                    }
                }
            }

            item {
                SectionCard(title = stringResource(R.string.field_transport_protocol)) {
                    DropdownField(
                        label = stringResource(R.string.field_transport_protocol),
                        selected = config.transport?.protocol ?: "tcp",
                        options = listOf("tcp", "kcp", "quic", "websocket", "wss"),
                        optionLabel = { it },
                        onSelect = { v ->
                            store.update {
                                it.copy(transport = (it.transport ?: TransportConfig()).copy(protocol = v))
                            }
                        },
                    )
                    SwitchRow(
                        label = stringResource(R.string.field_tcp_mux),
                        checked = config.transport?.tcpMux ?: true,
                        onCheckedChange = { v ->
                            store.update {
                                it.copy(transport = (it.transport ?: TransportConfig()).copy(tcpMux = v))
                            }
                        },
                    )
                    NumberField(
                        value = (config.transport?.poolCount ?: 0).toString(),
                        onValueChange = { v ->
                            store.update {
                                it.copy(transport = (it.transport ?: TransportConfig()).copy(poolCount = v.toIntOrNull() ?: 0))
                            }
                        },
                        label = stringResource(R.string.field_pool_count),
                    )
                    SwitchRow(
                        label = stringResource(R.string.field_tls_enable),
                        checked = config.transport?.tls?.enable ?: true,
                        onCheckedChange = { v ->
                            store.update {
                                val transport = it.transport ?: TransportConfig()
                                it.copy(transport = transport.copy(tls = (transport.tls ?: com.frpc.shared.model.TlsConfig()).copy(enable = v)))
                            }
                        },
                    )
                    LabeledTextField(
                        value = config.transport?.proxyURL.orEmpty(),
                        onValueChange = { v ->
                            store.update {
                                it.copy(transport = (it.transport ?: TransportConfig()).copy(proxyURL = v.ifBlank { null }))
                            }
                        },
                        label = stringResource(R.string.field_proxy_url),
                        placeholder = "http://user:pass@host:port",
                    )
                }
            }

            item {
                SectionCard(title = stringResource(R.string.field_log_level)) {
                    DropdownField(
                        label = stringResource(R.string.field_log_level),
                        selected = config.log?.level ?: "info",
                        options = listOf("trace", "debug", "info", "warn", "error"),
                        optionLabel = { it },
                        onSelect = { v ->
                            store.update {
                                it.copy(log = (it.log ?: LogConfig()).copy(level = v))
                            }
                        },
                    )
                    LabeledTextField(
                        value = (config.udpPacketSize ?: 1500).toString(),
                        onValueChange = { v ->
                            store.update { it.copy(udpPacketSize = v.toIntOrNull()) }
                        },
                        label = stringResource(R.string.field_udp_packet_size),
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    )
                }
            }

            // 代理规则
            item {
                SectionCard(
                    title = stringResource(R.string.proxies_title),
                    trailing = {
                        IconButton(onClick = {
                            navigator.push(Screen.ProxyEdit(current.profileId ?: "", config.proxies.size))
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        }
                    },
                ) {
                    if (config.proxies.isEmpty()) {
                        Text(
                            text = stringResource(R.string.proxies_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            itemsIndexedProxies(config, onEdit = { index ->
                navigator.push(Screen.ProxyEdit(current.profileId ?: "", index))
            }, onDelete = { index ->
                store.update {
                    it.copy(proxies = it.proxies.filterIndexed { i, _ -> i != index })
                }
            })

            // 访问者
            item {
                SectionCard(
                    title = stringResource(R.string.visitors_title),
                    trailing = {
                        IconButton(onClick = {
                            navigator.push(Screen.VisitorEdit(current.profileId ?: "", config.visitors.size))
                        }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                        }
                    },
                ) {
                    if (config.visitors.isEmpty()) {
                        Text(
                            text = stringResource(R.string.visitors_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            itemsIndexedVisitors(config, onEdit = { index ->
                navigator.push(Screen.VisitorEdit(current.profileId ?: "", index))
            }, onDelete = { index ->
                store.update {
                    it.copy(visitors = it.visitors.filterIndexed { i, _ -> i != index })
                }
            })
        }
    }

    showValidation?.let { issues ->
        AlertDialog(
            onDismissRequest = { showValidation = null },
            title = { Text(stringResource(R.string.validation_title)) },
            text = {
                Column {
                    issues.forEach { issue ->
                        Text(
                            text = "• " + validationMessage(issue),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showValidation = null }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
        )
    }
}

@Composable
private fun validationMessage(issue: ValidationIssue): String {
    val fieldLabel = stringResource(fieldLabelRes(issue.field))
    return stringResource(reasonTemplateRes(issue.reason), fieldLabel)
}

/** 代理列表行 */
private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedProxies(
    config: FrpcConfig,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
) {
    itemsIndexed(config.proxies) { index, proxy ->
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = proxy.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = proxy.type.uppercase() + " · " +
                            (proxy.localIP ?: "127.0.0.1") + ":" + (proxy.localPort?.toString() ?: "?"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onEdit(index) }) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                }
                IconButton(onClick = { onDelete(index) }) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedVisitors(
    config: FrpcConfig,
    onEdit: (Int) -> Unit,
    onDelete: (Int) -> Unit,
) {
    itemsIndexed(config.visitors) { index, visitor ->
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = visitor.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = visitor.type.uppercase() + " → " + visitor.serverName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onEdit(index) }) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                }
                IconButton(onClick = { onDelete(index) }) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                }
            }
        }
    }
}
