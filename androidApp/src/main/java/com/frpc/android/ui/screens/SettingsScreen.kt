package com.frpc.android.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frpc.android.R
import com.frpc.android.data.ThemeMode
import com.frpc.android.di.AppContainer
import com.frpc.android.service.FrpcSupervisor
import com.frpc.android.ui.components.DropdownField
import com.frpc.android.ui.components.MonoText
import com.frpc.android.ui.components.SectionCard
import com.frpc.android.ui.components.SwitchRow
import kotlinx.coroutines.launch
@Composable
fun SettingsScreen(container: AppContainer) {
    val context = LocalContext.current
    val themeMode by container.settingsRepository.themeMode.collectAsStateWithLifecycle()
    val autoStart by container.settingsRepository.autoStartOnBoot.collectAsStateWithLifecycle()
    val defaultProfileId by container.settingsRepository.defaultProfileId.collectAsStateWithLifecycle()
    val profiles by container.configRepository.profiles.collectAsStateWithLifecycle()
    val state by FrpcSupervisor.state.collectAsStateWithLifecycle()

    val powerManager = remember(context) { context.getSystemService<PowerManager>() }
    val batteryIgnored = remember(context) {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }
    val engineVersion = remember { container.binaryManager.bundledVersion() }
    val engineAbi = FrpcSupervisor.state.value.abi ?: container.binaryManager.deviceAbi() ?: stringResource(R.string.unknown)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(title = stringResource(R.string.settings_theme)) {
            DropdownField(
                label = stringResource(R.string.settings_theme),
                selected = themeMode,
                options = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK),
                optionLabel = { mode ->
                    when (mode) {
                        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        else -> mode
                    }
                },
                onSelect = { container.settingsRepository.setThemeMode(it) },
            )
        }

        SectionCard(title = stringResource(R.string.settings_startup)) {
            SwitchRow(
                label = stringResource(R.string.settings_startup),
                checked = autoStart,
                onCheckedChange = { container.settingsRepository.setAutoStartOnBoot(it) },
                supportingText = stringResource(R.string.settings_startup_summary),
            )
            if (profiles.isNotEmpty()) {
                DropdownField(
                    label = stringResource(R.string.settings_default_config),
                    selected = profiles.firstOrNull { it.id == defaultProfileId } ?: profiles.first(),
                    options = profiles,
                    optionLabel = { it.name },
                    onSelect = { container.settingsRepository.setDefaultProfileId(it.id) },
                )
            }
        }

        SectionCard(title = stringResource(R.string.settings_battery)) {
            Text(
                text = if (batteryIgnored) {
                    stringResource(R.string.settings_battery_ok)
                } else {
                    stringResource(R.string.settings_battery_summary)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!batteryIgnored) {
                TextButton(onClick = { requestIgnoreBatteryOptimizations(context) }) {
                    Text(stringResource(R.string.settings_battery_request))
                }
            }
        }

        SectionCard(title = stringResource(R.string.settings_binary)) {
            Text(
                text = stringResource(R.string.settings_binary_version, engineVersion, engineAbi),
                style = MaterialTheme.typography.bodyMedium,
            )
            // 运行时引擎实际路径：新版应为 /data/app/.../lib/arm64/libfrpc.so（jniLibs 解压位）
            MonoText(
                text = stringResource(
                    R.string.settings_binary_path,
                    container.binaryManager.binaryFile().absolutePath,
                    if (container.binaryManager.isReady()) "就绪" else "缺失",
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.settings_binary_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionCard(title = stringResource(R.string.settings_about)) {
            Text(
                text = stringResource(R.string.settings_about_version, "1.0.0"),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.settings_about_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "frp v0.71.1 · Kotlin Multiplatform + Compose",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    runCatching { context.startActivity(intent) }.onFailure {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
