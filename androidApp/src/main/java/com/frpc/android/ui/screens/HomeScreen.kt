package com.frpc.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frpc.android.R
import com.frpc.android.di.AppContainer
import com.frpc.android.service.FrpcService
import com.frpc.android.service.FrpcSupervisor
import com.frpc.android.ui.components.SectionCard
import com.frpc.android.ui.components.StatusChip
import com.frpc.android.ui.nav.AppNavigator
import com.frpc.android.ui.nav.Screen
import com.frpc.android.ui.theme.StatusError
import com.frpc.android.ui.theme.StatusIdle
import com.frpc.android.ui.theme.StatusRunning
import com.frpc.android.ui.theme.StatusStarting
import com.frpc.android.util.formatDuration
import com.frpc.android.util.formatMemoryKb
import com.frpc.android.util.formatPercent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(container: AppContainer, navigator: AppNavigator) {
    val context = LocalContext.current
    val state by FrpcSupervisor.state.collectAsStateWithLifecycle()
    val status by FrpcSupervisor.status.collectAsStateWithLifecycle()
    val stats by FrpcSupervisor.stats.collectAsStateWithLifecycle()
    val cpu by FrpcSupervisor.cpuPercent.collectAsStateWithLifecycle()
    val adminReachable by FrpcSupervisor.adminReachable.collectAsStateWithLifecycle()
    val profiles by container.configRepository.profiles.collectAsStateWithLifecycle()
    val defaultId by container.settingsRepository.defaultProfileId.collectAsStateWithLifecycle()

    val currentProfileId = state.profileId ?: defaultId
    val currentProfile = profiles.firstOrNull { it.id == currentProfileId }
    val scope = rememberCoroutineScope()

    // 运行时长秒级刷新
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.phase) {
        while (FrpcSupervisor.state.value.phase == FrpcSupervisor.Phase.RUNNING) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    val uptime = state.startedAtMs?.let { formatDuration(nowMs - it) } ?: "--"

    val phaseLabel = when (state.phase) {
        FrpcSupervisor.Phase.IDLE -> stringResource(R.string.phase_idle)
        FrpcSupervisor.Phase.STARTING -> stringResource(R.string.phase_starting)
        FrpcSupervisor.Phase.RUNNING -> stringResource(R.string.phase_running)
        FrpcSupervisor.Phase.STOPPING -> stringResource(R.string.phase_stopping)
        FrpcSupervisor.Phase.ERROR -> stringResource(R.string.phase_error)
    }
    val phaseColor = when (state.phase) {
        FrpcSupervisor.Phase.RUNNING -> StatusRunning
        FrpcSupervisor.Phase.STARTING -> StatusStarting
        FrpcSupervisor.Phase.ERROR -> StatusError
        else -> StatusIdle
    }

    val onlineCount = status?.all?.count { it.status == "running" } ?: 0
    val totalCount = currentProfile?.config?.proxies?.size ?: 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = stringResource(R.string.home_status)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(text = phaseLabel, color = phaseColor)
                    if (state.phase == FrpcSupervisor.Phase.RUNNING) {
                        Text(
                            text = " · ${state.profileName}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                if (state.error != null) {
                    Text(
                        text = state.error ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val running = state.phase == FrpcSupervisor.Phase.RUNNING
                    Button(
                        onClick = {
                            if (running) {
                                context.startService(FrpcService.stopIntent(context))
                            } else {
                                currentProfile?.let { profile ->
                                    ContextCompat.startForegroundService(
                                        context,
                                        FrpcService.startIntent(context, profile.id),
                                    )
                                }
                            }
                        },
                        enabled = currentProfile != null &&
                            state.phase != FrpcSupervisor.Phase.STARTING &&
                            state.phase != FrpcSupervisor.Phase.STOPPING,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.phase == FrpcSupervisor.Phase.STARTING ||
                            state.phase == FrpcSupervisor.Phase.STOPPING
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        } else {
                            Text(
                                stringResource(
                                    if (running) R.string.action_stop else R.string.action_start,
                                ),
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { context.startService(FrpcService.reloadIntent(context)) },
                        enabled = state.phase == FrpcSupervisor.Phase.RUNNING,
                    ) {
                        Text(stringResource(R.string.action_reload))
                    }
                }
                if (currentProfile == null) {
                    TextButton(onClick = { navigator.selectTab(Screen.ConfigList) }) {
                        Text(stringResource(R.string.home_no_config))
                    }
                }
            }
        }

        // 配置切换（多个配置时）
        if (profiles.size > 1) {
            item {
                SectionCard(title = stringResource(R.string.home_current_config)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        profiles.take(4).forEach { profile ->
                            FilterChip(
                                selected = profile.id == currentProfileId,
                                onClick = {
                                    scope.launch {
                                        container.settingsRepository.setDefaultProfileId(profile.id)
                                    }
                                },
                                label = { Text(profile.name, maxLines = 1) },
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.home_quick_stats)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatCell(stringResource(R.string.home_cpu), formatPercent(cpu))
                    StatCell(stringResource(R.string.home_memory), stats?.let { formatMemoryKb(it.rssKb) } ?: "--")
                    StatCell(stringResource(R.string.home_uptime), uptime)
                    StatCell(
                        stringResource(R.string.home_online_proxies),
                        if (state.adminEnabled) "$onlineCount/$totalCount" else "--",
                    )
                }
                if (state.phase == FrpcSupervisor.Phase.RUNNING && state.adminEnabled && !adminReachable) {
                    Text(
                        text = stringResource(R.string.home_admin_unreachable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (!state.adminEnabled && state.phase == FrpcSupervisor.Phase.RUNNING) {
                    Text(
                        text = stringResource(R.string.home_admin_off),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SectionCard(title = stringResource(R.string.home_proxies)) {
                val proxies = currentProfile?.config?.proxies ?: emptyList()
                if (proxies.isEmpty()) {
                    Text(
                        text = stringResource(R.string.proxies_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(proxiesOf(currentProfile)) { proxy ->
            val item = status?.item(proxy.name)
            val (label, color) = when (item?.status) {
                "running" -> "在线" to StatusRunning
                "error" -> "异常" to StatusError
                null -> if (state.phase == FrpcSupervisor.Phase.RUNNING) "离线" to StatusIdle else "--" to StatusIdle
                else -> item.status to StatusIdle
            }
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = proxy.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = buildString {
                                append(proxy.type.uppercase())
                                append(" · ")
                                append(proxy.localIP ?: "127.0.0.1")
                                append(":")
                                append(proxy.localPort?.toString() ?: "?")
                                if (proxy.remotePort != null) {
                                    append(" → :")
                                    append(proxy.remotePort.toString())
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (item?.err?.isNotBlank() == true) {
                            Text(
                                text = item.err ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    StatusChip(text = label, color = color)
                }
            }
        }
    }
}

private fun proxiesOf(profile: com.frpc.android.data.ServerProfile?) =
    profile?.config?.proxies ?: emptyList()

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
