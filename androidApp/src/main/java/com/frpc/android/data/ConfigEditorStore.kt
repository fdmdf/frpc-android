package com.frpc.android.data

import com.frpc.shared.model.FrpcConfig
import com.frpc.shared.model.ProxyConfig
import com.frpc.shared.model.VisitorConfig
import com.frpc.shared.validate.ConfigValidator
import com.frpc.shared.validate.ValidationIssue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * 配置编辑器草稿（Activity 级单例，跨旋转保存未保存的编辑）。
 * 同一时刻只有一个编辑流程，避免无 Navigation-Compose 时的 ViewModel key 管理复杂度。
 */
class ConfigEditorStore {

    data class Draft(
        val profileId: String?,
        val name: String,
        val config: FrpcConfig,
        val createdAt: Long = System.currentTimeMillis(),
        val lastUsedAt: Long = 0L,
    ) {
        val isNew: Boolean get() = profileId == null
    }

    private val _draft = MutableStateFlow<Draft?>(null)
    val draft: StateFlow<Draft?> = _draft.asStateFlow()

    private val _proxyDraft = MutableStateFlow<ProxyConfig?>(null)
    val proxyDraft: StateFlow<ProxyConfig?> = _proxyDraft.asStateFlow()

    private val _visitorDraft = MutableStateFlow<VisitorConfig?>(null)
    val visitorDraft: StateFlow<VisitorConfig?> = _visitorDraft.asStateFlow()

    // ------------------------------------------------------------ 服务器配置

    fun beginNew() {
        _draft.value = Draft(profileId = null, name = "", config = FrpcConfig())
    }

    fun beginEdit(profile: ServerProfile) {
        _draft.value = Draft(
            profileId = profile.id,
            name = profile.name,
            config = profile.config,
            createdAt = profile.createdAt,
            lastUsedAt = profile.lastUsedAt,
        )
    }

    fun rename(name: String) {
        _draft.value = _draft.value?.copy(name = name)
    }

    fun update(transform: (FrpcConfig) -> FrpcConfig) {
        _draft.value = _draft.value?.let { it.copy(config = transform(it.config)) }
    }

    fun clear() {
        _draft.value = null
    }

    /** 构建待保存的档案（保留原 id 与创建/使用时间，新建则生成新 id） */
    fun toProfile(): ServerProfile? {
        val draft = _draft.value ?: return null
        val now = System.currentTimeMillis()
        return ServerProfile(
            id = draft.profileId ?: UUID.randomUUID().toString(),
            name = draft.name,
            config = draft.config,
            createdAt = draft.createdAt,
            updatedAt = now,
            lastUsedAt = draft.lastUsedAt,
        )
    }

    fun validate(portInUse: (Int) -> Boolean): List<ValidationIssue> {
        val config = _draft.value?.config ?: return emptyList()
        return ConfigValidator.validate(config, portInUse)
    }

    // ------------------------------------------------------------ 代理草稿

    fun beginProxyEdit(proxy: ProxyConfig?) {
        _proxyDraft.value = proxy ?: ProxyConfig(name = "", type = "tcp")
    }

    fun updateProxy(transform: (ProxyConfig) -> ProxyConfig) {
        _proxyDraft.value = _proxyDraft.value?.let { transform(it) }
    }

    fun clearProxy() {
        _proxyDraft.value = null
    }

    /** 保存代理到配置草稿：index 处替换，越界则追加 */
    fun commitProxy(index: Int, proxy: ProxyConfig) {
        update { config ->
            val list = config.proxies.toMutableList()
            if (index in list.indices) list[index] = proxy else list += proxy
            config.copy(proxies = list)
        }
        clearProxy()
    }

    // ------------------------------------------------------------ 访问者草稿

    fun beginVisitorEdit(visitor: VisitorConfig?) {
        _visitorDraft.value = visitor ?: VisitorConfig(name = "", type = "stcp", serverName = "")
    }

    fun updateVisitor(transform: (VisitorConfig) -> VisitorConfig) {
        _visitorDraft.value = _visitorDraft.value?.let { transform(it) }
    }

    fun clearVisitor() {
        _visitorDraft.value = null
    }

    fun commitVisitor(index: Int, visitor: VisitorConfig) {
        update { config ->
            val list = config.visitors.toMutableList()
            if (index in list.indices) list[index] = visitor else list += visitor
            config.copy(visitors = list)
        }
        clearVisitor()
    }
}
