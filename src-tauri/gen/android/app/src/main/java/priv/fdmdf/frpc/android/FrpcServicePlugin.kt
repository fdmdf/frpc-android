package priv.fdmdf.frpc.android

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.tauri.annotation.Command
import app.tauri.annotation.TauriPlugin
import app.tauri.plugin.Invoke
import app.tauri.plugin.JSObject
import app.tauri.plugin.Plugin

/**
 * 暴露给 Rust 侧的 Android 能力：
 * - 查询 nativeLibraryDir（frpc 以 libfrpc.so 的形式安装在这里）
 * - 启停前台服务，保证 frpc 在后台存活
 * - 用系统浏览器打开链接
 */
@TauriPlugin
class FrpcServicePlugin(private val activity: Activity) : Plugin(activity) {

    @Command
    fun getAppInfo(invoke: Invoke) {
        val result = JSObject()
        result.put("nativeLibDir", activity.applicationInfo.nativeLibraryDir)
        result.put("sdkInt", Build.VERSION.SDK_INT)
        invoke.resolve(result)
    }

    @Command
    fun startService(invoke: Invoke) {
        ensureNotificationPermission()
        val intent = Intent(activity, FrpcForegroundService::class.java)
        intent.putExtra(FrpcForegroundService.EXTRA_RUNNING, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startForegroundService(intent)
        } else {
            activity.startService(intent)
        }
        invoke.resolve()
    }

    @Command
    fun stopService(invoke: Invoke) {
        activity.stopService(Intent(activity, FrpcForegroundService::class.java))
        invoke.resolve()
    }

    @Command
    fun updateNotification(invoke: Invoke) {
        val running = invoke.getArgs().optBoolean("running", false)
        FrpcForegroundService.update(running)
        invoke.resolve()
    }

    @Command
    fun openUrl(invoke: Invoke) {
        val url = invoke.getArgs().optString("url", "")
        if (url.isBlank()) {
            invoke.reject("url is empty", null, null, null)
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
            invoke.resolve()
        } catch (e: Exception) {
            invoke.reject(e.message ?: "open url failed", null, e, null)
        }
    }

    /** Android 13 起前台服务通知需要用户授权，未授权时服务仍可运行但没有横幅提示。 */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_REQUEST_CODE
            )
        }
    }

    private companion object {
        const val NOTIFICATION_REQUEST_CODE = 0x4652
    }
}
