package com.frpc.shared.toml

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlIndentation
import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.TomlOutputConfig
import com.akuleshov7.ktoml.decoders.TomlMainDecoder
import com.akuleshov7.ktoml.parsers.TomlParser
import com.akuleshov7.ktoml.tree.nodes.TomlNode
import com.akuleshov7.ktoml.tree.nodes.TomlTable
import com.frpc.shared.model.FrpcConfig

/**
 * frpc.toml 编解码门面。
 * 导入时忽略未建模字段（插件 / 健康检查 / 注解等，见 README 已知限制）。
 */
object FrpcToml {

    private val inputConfig = TomlInputConfig(
        ignoreUnknownNames = true,
        allowEmptyToml = true,
    )
    private val outputConfig = TomlOutputConfig(
        indentation = TomlIndentation.FOUR_SPACES,
    )
    private val format = Toml(inputConfig = inputConfig, outputConfig = outputConfig)
    private val parser = TomlParser(inputConfig)

    fun encode(config: FrpcConfig): String =
        format.encodeToString(FrpcConfig.serializer(), config)

    fun decode(text: String): FrpcConfig {
        val tree = parser.parseString(text)
        mergeDuplicateTables(tree)
        val config = TomlMainDecoder.decode(FrpcConfig.serializer(), tree, inputConfig)
        // 导入时按代理类型清理不支持的字段（frp 严格解码，多余字段会导致启动失败）
        return config.copy(
            proxies = config.proxies.map { it.sanitizedForType() },
        )
    }

    /**
     * 绕过 ktoml 0.7.1 缺陷：[[proxies]] 等数组表元素内的点分键
     * （如 transport.bandwidthLimit = "1MB"）会为每个键各生成一个同名子表，
     * 导致 kotlinx 解码时字段丢失。
     * TOML 语义规定同一父节点下表名唯一，故同名子表必然是解析产物，直接合并。
     */
    private fun mergeDuplicateTables(node: TomlNode) {
        node.children.toList().forEach { mergeDuplicateTables(it) }
        node.children
            .filterIsInstance<TomlTable>()
            .groupBy { it.name }
            .filter { it.value.size > 1 }
            .forEach { (_, group) ->
                val first = group.first()
                group.drop(1).forEach { stale ->
                    stale.children.toList().forEach { child ->
                        stale.children.remove(child)
                        first.appendChild(child)
                    }
                    node.children.remove(stale)
                }
            }
    }
}
