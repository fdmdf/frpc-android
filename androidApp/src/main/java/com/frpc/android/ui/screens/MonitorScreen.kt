package com.frpc.android.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frpc.android.R
import com.frpc.android.di.AppContainer
import com.frpc.android.service.FrpcSupervisor
import com.frpc.android.ui.components.SectionCard
import com.frpc.android.ui.components.StatusChip
import com.frpc.android.ui.theme.StatusError
import com.frpc.android.ui.theme.StatusIdle
import com.frpc.android.ui.theme.StatusRunning
import com.frpc.android.util.formatDuration
import com.frpc.android.util.formatMemoryKb
import com.frpc.android.util.formatPercent
import kotlinx.coroutines.delay

@Composable
fun MonitorScreen(container: AppContainer) {
    val state by FrpcSupervisor.state.collectAsStateWithLifecycle()
    val stats by FrpcSupervisor.stats.collectAsStateWithLifecycle()
    val cpu by FrpcSupervisor.cpuPercent.collectAsStateWithLifecycle()
    val status by FrpcSupervisor.status.collectAsStateWithLifecycle()

    val cpuHistory = remember { mutableStateListOf<Float>() }
    val memHistory = remember { mutableStateListOf<Float>() }

    LaunchedEffect(Unit) {
        while (true) {
            cpu?.let { cpuHistory += it }
            stats?.let { memHistory += (it.rssKb / 1024f) }
            if (cpuHistory.size > 60) cpuHistory.removeAt(0)
            if (memHistory.size > 60) memHistory.removeAt(0)
            delay(2000)
        }
    }

    if (state.phase != FrpcSupervisor.Phase.RUNNING) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.monitor_not_running),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }
    val uptime = state.startedAtMs?.let { formatDuration(nowMs - it) } ?: "--"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard(title = stringResource(R.string.monitor_process)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatCell2(stringResource(R.string.home_pid), state.pid?.toString() ?: "--")
                    StatCell2(stringResource(R.string.monitor_threads), stats?.threads?.toString() ?: "--")
                    StatCell2(stringResource(R.string.monitor_state), stats?.state?.toString() ?: "--")
                    StatCell2(stringResource(R.string.home_uptime), uptime)
                }
            }
        }
        item {
            SectionCard(title = stringResource(R.string.monitor_cpu_usage)) {
                Text(
                    text = formatPercent(cpu),
                    style = MaterialTheme.typography.titleMedium,
                )
                LineChart(
                    values = cpuHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.monitor_mem_usage)) {
                Text(
                    text = stats?.let { formatMemoryKb(it.rssKb) } ?: "--",
                    style = MaterialTheme.typography.titleMedium,
                )
                LineChart(
                    values = memHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                )
            }
        }
        item {
            SectionCard(title = stringResource(R.string.monitor_proxy_status)) {
                val items = status?.all ?: emptyList()
                if (items.isEmpty()) {
                    Text(
                        text = if (state.adminEnabled) "--" else stringResource(R.string.home_admin_off),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        itemsCounted(status?.all ?: emptyList()) { item ->
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = item.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = item.type.uppercase() + " · " +
                                (item.remoteAddr.ifBlank { item.localAddr }),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (item.err.isNotBlank()) {
                            Text(
                                text = item.err,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    val (label, color) = when (item.status) {
                        "running" -> "在线" to StatusRunning
                        "error" -> "异常" to StatusError
                        else -> item.status to StatusIdle
                    }
                    StatusChip(text = label, color = color)
                }
            }
        }
    }
}

@Composable
private fun StatCell2(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleMedium)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 简易折线图（Canvas，无图表依赖） */
@Composable
private fun LineChart(values: List<Float>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val maxValue = (values.max() * 1.2f).coerceAtLeast(1f)
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index * stepX
            val y = size.height - (value / maxValue) * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 2f))
        // 末端点
        val lastValue = values.last()
        val lastY = size.height - (lastValue / maxValue) * size.height
        drawCircle(color = lineColor, radius = 3f, center = Offset(size.width, lastY))
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsCounted(
    items: List<com.frpc.shared.admin.ProxyStatusItem>,
    content: @Composable (com.frpc.shared.admin.ProxyStatusItem) -> Unit,
) {
    items(items.size) { index -> content(items[index]) }
}
