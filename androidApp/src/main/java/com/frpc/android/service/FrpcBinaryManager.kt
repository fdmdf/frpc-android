package com.frpc.android.service

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * frpc 二进制管理：按设备 ABI 从 assets 安装到 filesDir/bin，支持版本比对与重装。
 * 二进制由 scripts/build-frpc.sh 从 frp 官方源码交叉编译（arm64-v8a / x86_64）。
 */
class FrpcBinaryManager(private val context: Context) {

    /** 本仓库随包的 ABI（armv7 因 Go 不支持纯静态链接未提供，见 README） */
    private val supportedAbis = setOf("arm64-v8a", "x86_64")

    private val binDir: File get() = File(context.filesDir, "bin")
    private val binaryFile: File get() = File(binDir, "frpc")
    private val versionFile: File get() = File(binDir, "frpc.version")

    /** 当前设备首选 ABI */
    fun deviceAbi(): String? = Build.SUPPORTED_ABIS.firstOrNull { it in supportedAbis }

    fun isAbiSupported(): Boolean = deviceAbi() != null

    /** assets 中随包 frpc 版本号 */
    fun bundledVersion(): String = runCatching {
        context.assets.open("$ASSET_ROOT/VERSION").bufferedReader().use { it.readText().trim() }
    }.getOrDefault("unknown")

    /** 已安装且版本一致 */
    fun isInstalledCurrent(): Boolean {
        if (!binaryFile.exists() || !binaryFile.canExecute()) return false
        if (!versionFile.exists()) return false
        return versionFile.readText().trim() == bundledVersion()
    }

    /** 确保安装可用二进制；返回可执行文件。ABI 不支持或安装失败时抛异常 */
    suspend fun ensureInstalled(): File = withContext(Dispatchers.IO) {
        val abi = deviceAbi() ?: throw IllegalStateException(
            "unsupported abi: ${Build.SUPPORTED_ABIS.joinToString()}",
        )
        if (isInstalledCurrent()) return@withContext binaryFile
        install(abi)
        binaryFile
    }

    /** 强制重装（设置页「重新安装引擎」） */
    suspend fun reinstall(): File = withContext(Dispatchers.IO) {
        val abi = deviceAbi() ?: throw IllegalStateException(
            "unsupported abi: ${Build.SUPPORTED_ABIS.joinToString()}",
        )
        binaryFile.delete()
        versionFile.delete()
        install(abi)
        binaryFile
    }

    private fun install(abi: String) {
        binDir.mkdirs()
        val tmp = File(binDir, "frpc.tmp")
        context.assets.open("$ASSET_ROOT/$abi/frpc").use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        if (!tmp.renameTo(binaryFile)) {
            binaryFile.writeBytes(tmp.readBytes())
            tmp.delete()
        }
        binaryFile.setExecutable(true)
        versionFile.writeText(bundledVersion())
    }

    private companion object {
        const val ASSET_ROOT = "frpc"
    }
}
