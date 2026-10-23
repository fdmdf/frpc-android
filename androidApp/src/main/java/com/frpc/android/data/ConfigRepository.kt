package com.frpc.android.data

import com.frpc.shared.toml.FrpcToml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * 配置档案仓库：单 JSON 文件持久化（原子写：tmp + rename）。
 * 配置文件规模小（数十条），JSON 文件足够且无需引入数据库依赖，保持包体最小。
 */
class ConfigRepository(private val file: File) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()
    private val _profiles = MutableStateFlow<List<ServerProfile>>(emptyList())
    val profiles: StateFlow<List<ServerProfile>> = _profiles.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!file.exists()) return@withContext
            runCatching {
                _profiles.value = json.decodeFromString<List<ServerProfile>>(file.readText())
            }
        }
        Unit
    }

    fun find(id: String?): ServerProfile? =
        id?.let { pid -> _profiles.value.firstOrNull { it.id == pid } }

    suspend fun upsert(profile: ServerProfile) {
        val stamped = profile.copy(updatedAt = System.currentTimeMillis())
        persist(_profiles.value.filter { it.id != profile.id } + stamped)
    }

    suspend fun delete(id: String) {
        persist(_profiles.value.filter { it.id != id })
    }

    suspend fun duplicate(id: String): ServerProfile? {
        val source = find(id) ?: return null
        val now = System.currentTimeMillis()
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name} 副本",
            createdAt = now,
            updatedAt = now,
            lastUsedAt = 0L,
        )
        upsert(copy)
        return copy
    }

    suspend fun markUsed(id: String) {
        val target = find(id) ?: return
        persist(_profiles.value.map { if (it.id == id) it.copy(lastUsedAt = System.currentTimeMillis()) else it })
    }

    /** 生成可直接交付 frpc 使用的 TOML 文本 */
    fun tomlOf(profile: ServerProfile): String = FrpcToml.encode(profile.config)

    /** 从 frpc.toml 文本导入（未建模字段按设计丢弃，见 README） */
    fun importToml(name: String, toml: String): ServerProfile =
        ServerProfile(name = name, config = FrpcToml.decode(toml))

    private suspend fun persist(list: List<ServerProfile>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val parent = file.absoluteFile.parentFile ?: return@withContext
            parent.mkdirs()
            val tmp = File(parent, "${file.name}.tmp")
            tmp.writeText(json.encodeToString<List<ServerProfile>>(list))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
            _profiles.value = list
        }
        Unit
    }
}
