package com.frpc.android.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frpc.android.R
import com.frpc.android.data.ServerProfile
import com.frpc.android.di.AppContainer
import com.frpc.android.ui.components.SectionCard
import com.frpc.android.ui.components.StatusChip
import com.frpc.android.ui.nav.AppNavigator
import com.frpc.android.ui.nav.Screen
import com.frpc.android.ui.theme.StatusIdle
import kotlinx.coroutines.launch

@Composable
fun ConfigListScreen(container: AppContainer, navigator: AppNavigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profiles by container.configRepository.profiles.collectAsStateWithLifecycle()
    val defaultId by container.settingsRepository.defaultProfileId.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<ServerProfile?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: error("cannot open file")
                }.onSuccess { text ->
                    val profile = container.configRepository.importToml("导入配置", text)
                    container.configRepository.upsert(profile)
                    Toast.makeText(
                        context,
                        context.getString(R.string.config_imported, profile.name),
                        Toast.LENGTH_SHORT,
                    ).show()
                }.onFailure { e ->
                    importError = e.message ?: "unknown"
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (profiles.isEmpty()) {
                item {
                    SectionCard {
                        Text(
                            text = stringResource(R.string.home_no_config),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            items(profiles, key = { it.id }) { profile ->
                ProfileCard(
                    profile = profile,
                    isDefault = profile.id == defaultId,
                    onEdit = { navigator.push(Screen.ServerEdit(profile.id)) },
                    onDuplicate = { scope.launch { container.configRepository.duplicate(profile.id) } },
                    onExport = { shareToml(context, container.configRepository.tomlOf(profile)) },
                    onSetDefault = {
                        scope.launch { container.settingsRepository.setDefaultProfileId(profile.id) }
                    },
                    onDelete = { pendingDelete = profile },
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                container.configEditorStore.beginNew()
                navigator.push(Screen.ServerEdit(null))
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.config_new)) },
        )
    }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.config_delete_title)) },
            text = { Text(stringResource(R.string.config_delete_confirm, profile.name)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { container.configRepository.delete(profile.id) }
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    importError?.let { message ->
        AlertDialog(
            onDismissRequest = { importError = null },
            title = { Text(stringResource(R.string.config_import_title)) },
            text = { Text(stringResource(R.string.config_import_failed, message)) },
            confirmButton = {
                TextButton(onClick = { importError = null }) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
        )
    }

    // 导入入口放在列表底部按钮行
    if (profiles.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            TextButton(onClick = { importLauncher.launch(arrayOf("text/plain", "application/toml")) }) {
                Text(stringResource(R.string.action_import) + " frpc.toml")
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: ServerProfile,
    isDefault: Boolean,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = profile.name, style = MaterialTheme.typography.titleMedium)
                    if (isDefault) {
                        StatusChip(
                            text = stringResource(R.string.config_default),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Text(
                    text = profile.serverSummary + " · " +
                        stringResource(R.string.proxies_title) + " " + profile.config.proxies.size,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onEdit) { Text(stringResource(R.string.action_edit)) }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_copy)) },
                        onClick = { menuExpanded = false; onDuplicate() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_export)) },
                        onClick = { menuExpanded = false; onExport() },
                    )
                    if (!isDefault) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.config_set_default)) },
                            onClick = { menuExpanded = false; onSetDefault() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = { menuExpanded = false; onDelete() },
                    )
                }
            }
        }
    }
}

private fun shareToml(context: android.content.Context, toml: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, toml)
        putExtra(Intent.EXTRA_TITLE, "frpc.toml")
    }
    context.startActivity(Intent.createChooser(intent, "frpc.toml"))
}
