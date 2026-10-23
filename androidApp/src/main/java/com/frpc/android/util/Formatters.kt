package com.frpc.android.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** 运行时长：1d 02:03:04 / 02:03:04 */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00:00"
    val days = TimeUnit.MILLISECONDS.toDays(ms)
    val hours = TimeUnit.MILLISECONDS.toHours(ms) % 24
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (days > 0) {
        String.format(Locale.US, "%dd %02d:%02d:%02d", days, hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }
}

/** 内存 KB → "12.3 MB" / "1.2 GB" */
fun formatMemoryKb(kb: Long): String = when {
    kb < 1024 -> "$kb KB"
    kb < 1024 * 1024 -> String.format(Locale.US, "%.1f MB", kb / 1024.0)
    else -> String.format(Locale.US, "%.2f GB", kb / 1024.0 / 1024.0)
}

fun formatPercent(value: Float?): String =
    if (value == null) "--" else String.format(Locale.US, "%.1f%%", value)

private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

fun formatLogTime(epochMs: Long): String = timeFormat.format(Date(epochMs))

/** 本地端口占用检测（真实绑定尝试，用于启动前校验） */
fun isPortInUse(port: Int): Boolean = try {
    java.net.ServerSocket(port).use { false }
} catch (e: Exception) {
    true
}
