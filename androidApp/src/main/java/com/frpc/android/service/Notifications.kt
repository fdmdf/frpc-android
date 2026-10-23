package com.frpc.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.frpc.android.MainActivity
import com.frpc.android.R

/** 通知：常驻状态（低优先级）+ 事件提醒（默认优先级） */
object Notifications {

    const val CHANNEL_STATUS = "frpc_status"
    const val CHANNEL_EVENTS = "frpc_events"
    const val ONGOING_ID = 1001
    private const val EVENT_ID = 1002

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val statusChannel = NotificationChannel(
            CHANNEL_STATUS,
            context.getString(R.string.notification_channel_status),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "frpc 运行状态常驻通知"
            setShowBadge(false)
        }
        val eventChannel = NotificationChannel(
            CHANNEL_EVENTS,
            context.getString(R.string.notification_channel_events),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "frpc 异常与事件提醒"
        }
        manager.createNotificationChannels(listOf(statusChannel, eventChannel))
    }

    fun buildStatusNotification(
        context: Context,
        state: FrpcSupervisor.RuntimeState,
        onlineCount: Int,
        totalCount: Int,
    ): Notification {
        val title = when (state.phase) {
            FrpcSupervisor.Phase.STARTING -> context.getString(R.string.notification_starting)
            FrpcSupervisor.Phase.RUNNING ->
                context.getString(R.string.notification_running, state.profileName, onlineCount, totalCount)
            FrpcSupervisor.Phase.STOPPING -> context.getString(R.string.notification_stopped)
            FrpcSupervisor.Phase.ERROR -> context.getString(R.string.notification_error, state.error ?: "")
            FrpcSupervisor.Phase.IDLE -> context.getString(R.string.notification_stopped)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            context,
            1,
            FrpcService.stopIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_frpc)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(title)
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.action_stop), stopIntent)
            .build()
    }

    fun updateStatusNotification(
        context: Context,
        state: FrpcSupervisor.RuntimeState,
        onlineCount: Int,
        totalCount: Int,
    ) {
        val notification = buildStatusNotification(context, state, onlineCount, totalCount)
        NotificationManagerCompat.from(context).notify(ONGOING_ID, notification)
    }

    fun postEvent(context: Context, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setSmallIcon(R.drawable.ic_stat_frpc)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(EVENT_ID, notification)
    }
}
