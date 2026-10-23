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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
    val draft by store.draft.collectAsStateWithLifecycle()

    LaunchedEffect(visitorIndex) {
        val existing = draft?.config?.visitors?.getOrNull(visitorIndex)
        store.beginVisitorEdit(existing)
    }

    val current = visitor ?: return

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
                        store.commitVisitor(visitorIndex, current)
                        navigator.pop()
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
                    onValueChange = { v -> store.updateVisitor { it.copy(name = v) } },
                    label = stringResource(R.string.field_visitor_name),
                )
                DropdownField(
                    label = stringResource(R.string.field_visitor_type),
                    selected = current.type,
                    options = listOf("stcp", "xtcp"),
                    optionLabel = { it.uppercase() },
                    onSelect = { v -> store.updateVisitor { it.copy(type = v) } },
                )
                LabeledTextField(
                    value = current.serverName,
                    onValueChange = { v -> store.updateVisitor { it.copy(serverName = v) } },
                    label = stringResource(R.string.field_server_name),
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
                    label = stringResource(R.string.field_bind_addr),
                )
                NumberField(
                    value = current.bindPort.toString(),
                    onValueChange = { v ->
                        store.updateVisitor { it.copy(bindPort = v.toIntOrNull() ?: 0) }
                    },
                    label = stringResource(R.string.field_bind_port),
                    supportingText = "0 = 不监听，仅接收转发",
                )
                LabeledTextField(
                    value = current.serverUser.orEmpty(),
                    onValueChange = { v ->
                        store.updateVisitor { it.copy(serverUser = v.ifBlank { null }) }
                    },
                    label = stringResource(R.string.field_user),
                )
                SwitchRow(
                    label = stringResource(R.string.field_keep_tunnel_open),
                    checked = current.keepTunnelOpen ?: false,
                    onCheckedChange = { v ->
                        store.updateVisitor { it.copy(keepTunnelOpen = v) }
                    },
                )
            }
        }
    }
}
