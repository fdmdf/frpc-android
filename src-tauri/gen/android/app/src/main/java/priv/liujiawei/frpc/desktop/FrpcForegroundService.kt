package priv.liujiawei.frpc.desktop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * frpc 运行期间的前台服务。
 *
 * Android 会在应用退到后台后冻结/回收进程，frpc 子进程也会跟着被杀掉。
 * 前台服务把进程提升为前台优先级，并在通知栏给出常驻提示。
 *
 * 这里不负责启动 frpc —— 进程仍然由 Rust 侧（ProcessManager）拉起，
 * 服务只负责“占住”进程优先级。
 */
class FrpcForegroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = intent?.getBooleanExtra(EXTRA_RUNNING, true) ?: true
        instance = this
        createChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(running),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            }
        )
        acquireWakeLock()
        return START_STICKY
    }

    override fun onDestroy() {
        instance = null
        releaseWakeLock()
        super.onDestroy()
    }

    fun setRunning(value: Boolean) {
        running = value
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(value))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.frpc_service_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.frpc_service_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(connected: Boolean): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.frpc_service_title))
            .setContentText(
                getString(
                    if (connected) R.string.frpc_service_running else R.string.frpc_service_stopped
                )
            )
            .setSmallIcon(R.drawable.ic_stat_frpc)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "frpc:service").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        const val CHANNEL_ID = "frpc_service"
        const val NOTIFICATION_ID = 0x4652
        const val EXTRA_RUNNING = "running"

        @Volatile
        private var instance: FrpcForegroundService? = null

        fun update(running: Boolean) {
            instance?.setRunning(running)
        }
    }
}
