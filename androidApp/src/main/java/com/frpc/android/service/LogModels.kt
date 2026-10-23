package com.frpc.android.service

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class LogLevel { TRACE, DEBUG, INFO, WARN, ERROR }

data class LogLine(
    val timeMs: Long,
    val level: LogLevel,
    /** frpc 输出的 [source] 位置，如 service.go:214 */
    val tag: String,
    val message: String,
)

/**
 * frpc 日志行解析。
 * frpc（golib logger）输出格式：2026-10-23 18:57:36.952 [I] [sub/root.go:194] message
 * - 日期分隔符兼容 - 与 /（不同 frp 版本）
 * - 防御性剥离 ANSI 颜色码（log.disablePrintColor=false 时理论上会带色）
 */
object FrpcLogParser {

    private val PATTERN = Regex(
        """^(\d{4}[-/]\d{2}[-/]\d{2} \d{2}:\d{2}:\d{2}\.\d{3}) \[([TDIWE])\] \[([^\]]*)] ?(.*)$""",
    )
    private val ANSI = Regex("""\u001B\[[0-9;]*[A-Za-z]""")
    private val FORMAT_DASH: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val FORMAT_SLASH: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS")

    fun parse(raw: String, nowFallbackMs: Long = System.currentTimeMillis()): LogLine {
        val cleaned = ANSI.replace(raw.trimEnd(), "")
        val match = PATTERN.matchEntire(cleaned)
        if (match != null) {
            val (timestamp, levelChar, tag, message) = match.destructured
            val timeMs = runCatching {
                LocalDateTime.parse(timestamp, FORMAT_DASH)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }.recoverCatching {
                LocalDateTime.parse(timestamp, FORMAT_SLASH)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            }.getOrDefault(nowFallbackMs)
            return LogLine(timeMs, levelOf(levelChar), tag, message)
        }
        return LogLine(nowFallbackMs, LogLevel.INFO, "", cleaned)
    }

    private fun levelOf(c: String): LogLevel = when (c) {
        "T" -> LogLevel.TRACE
        "D" -> LogLevel.DEBUG
        "W" -> LogLevel.WARN
        "E" -> LogLevel.ERROR
        else -> LogLevel.INFO
    }
}
