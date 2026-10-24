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
import com.frpc.shared.validate.ConfigValidator
import com.frpc.shared.validate.Severity
import com.frpc.shared.validate.ValidationIssue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitorEditScreen(
    container: AppContainer,
    navigator: AppNavigator,
    profileId: String,
    visitorIndex: Int,
) {
    val store = container.configEditorStore
    val visitor by store.visitorDraft.collectAsStateWithLifecycle()
    var issues by remember { mutableStateOf<List<ValidationIssue>>(emptyList()) }

    // 防重入：旋转/重绘时若草稿已属于该下标则跳过初始化（避免丢失未保存输入）
    LaunchedEffect(visitorIndex) {
        if (store.hasVisitorDraft(visitorIndex)) return@LaunchedEffect
        val existing = store.draft.value?.config?.visitors?.getOrNull(visitorIndex)
        store.beginVisitorEdit(visitorIndex, existing)
    }

    val current = visitor ?: return
    val errorFields = remember(issues) { issues.map { it.formFieldKey() }.toSet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.visitor_edit)) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        // 复用 shared 层校验器（单访问者临时配置），必填项不满足则阻止保存
                        val visitorIssues = ConfigValidator.validate(
                            FrpcConfig(
                                serverAddr = "validation",
                                auth = AuthConfig(token = "validation"),
                                visitors = listOf(current),
                            ),
                        ).filter { it.field.startsWith("visitors[") }
                        val blocking = visitorIssues.filter { it.severity == Severity.ERROR }
                        if (blocking.isNotEmpty()) {
                            issues = blocking
                        } else {
                            store.commitVisitor(visitorIndex, current)
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
                    onValueChange = { v -> store.updateVisitor { it.copy(name = v) } },
                    label = stringResource(R.string.field_visitor_name) + " *",
                    isError = "name" in errorFields,
                )
                DropdownField(
                    label = stringResource(R.string.field_visitor_type) + " *",
                    selected = current.type,
                    options = listOf("stcp", "xtcp"),
                    optionLabel = { it.uppercase() },
                    onSelect = { v ->
                        // 切类型时清掉不适用字段（keepTunnelOpen 仅 xtcp 有）
                        store.updateVisitor { it.copy(type = v).sanitizedForType() }
                    },
                )
                LabeledTextField(
                    value = current.serverName,
                    onValueChange = { v -> store.updateVisitor { it.copy(serverName = v) } },
                    label = stringResource(R.string.field_server_name) + " *",
                    isError = "serverName" in errorFields,
                )
                LabeledTextField(
                    value = current.secretKey.orEmpty(),
                    onValueChange = { v ->
                        store.updateVisitor { it.copy(secretKey = v.ifBlank { null }) }
                    },
                    label = stringResource(R.string.field_secret_key),
                )
            }
            SectionCard {
                LabeledTextField(
                    value = current.bindAddr,
                    onValueChange = { v -> store.updateVisitor { it.copy(bindAddr = v) } },
                    label = stringResource(R.string.field_bind_addr) + " *",
                    isError = "bindAddr" in errorFields,
                )
                NumberField(
                    value = current.bindPort.toString(),
                    onValueChange = { v ->
                        store.updateVisitor { it.copy(bindPort = v.toIntOrNull() ?: 0) }
                    },
                    label = stringResource(R.string.field_bind_port) + " *",
                    supportingText = "0 = 不监听，仅接收转发",
                    isError = "bindPort" in errorFields,
                )
                LabeledTextField(
                    value = current.serverUser.orEmpty(),
                    onValueChange = { v ->
                        store.updateVisitor { it.copy(serverUser = v.ifBlank { null }) }
                    },
                    label = stringResource(R.string.field_user),
                )
                if (current.type == "xtcp") {
                    SwitchRow(
                        label = stringResource(R.string.field_keep_tunnel_open),
                        checked = current.keepTunnelOpen ?: false,
                        onCheckedChange = { v ->
                            store.updateVisitor { it.copy(keepTunnelOpen = v) }
                        },
                        supportingText = "仅 xtcp 支持；stcp 访问者带上会让 frpc 启动失败",
                    )
                }
            }
        }
    }
}
