package storage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.kebab.v2rayk.wrapper.config.V2rayProperties
import java.io.File
import java.io.IOException

/**
 * 基于 JSON 文件的节点配置存储实现。
 *
 * 文件结构：{user.home}/.v2rayk/nodes/{group}/{nodeName}.json
 * 序列化时使用 Jackson 将 V2rayProperties 写入 JSON 文件。
 */
class JsonFileNodeRepository : NodeRepository {

    private val baseDir: File by lazy {
        val home = System.getProperty("user.home")
            ?: throw IllegalStateException("无法获取 user.home 系统属性")
        File(home, ".v2rayk/nodes").also { it.mkdirs() }
    }

    private val mapper = jacksonObjectMapper()

    override fun save(group: String, nodeName: String, config: V2rayProperties): String {
        val groupDir = File(baseDir, sanitize(group))
        if (!groupDir.exists()) {
            val created = groupDir.mkdirs()
            if (!created) throw IOException("无法创建目录: ${groupDir.absolutePath}")
        }

        val configFile = File(groupDir, "${sanitize(nodeName)}.json")
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config)
        } catch (e: Exception) {
            throw IOException("写入配置文件失败: ${configFile.absolutePath}", e)
        }
        return configFile.absolutePath
    }

    override fun loadAll(): Map<String, List<Pair<String, V2rayProperties>>> {
        if (!baseDir.exists() || !baseDir.isDirectory) return emptyMap()

        val result = mutableMapOf<String, List<Pair<String, V2rayProperties>>>()

        baseDir.listFiles()?.filter { it.isDirectory }?.forEach { groupDir ->
            val groupName = groupDir.name
            val nodes = groupDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.mapNotNull { configFile ->
                    try {
                        val config: V2rayProperties = mapper.readValue(configFile)
                        val nodeName = configFile.nameWithoutExtension
                        nodeName to config
                    } catch (e: Exception) {
                        System.err.println("[WARN] 跳过损坏的配置文件: ${configFile.absolutePath} - ${e.message}")
                        null
                    }
                } ?: emptyList()
            if (nodes.isNotEmpty()) {
                result[groupName] = nodes
            }
        }

        return result
    }

    override fun delete(group: String, nodeName: String) {
        val configFile = File(baseDir, "${sanitize(group)}/${sanitize(nodeName)}.json")
        if (configFile.exists()) {
            val deleted = configFile.delete()
            if (!deleted) System.err.println("[WARN] 删除配置文件失败: ${configFile.absolutePath}")
        }
    }

    /**
     * 清理文件名中的非法字符，替换为下划线。
     */
    private fun sanitize(name: String): String {
        return name.replace(Regex("""[<>:"/\\|?*]"""), "_").trim()
    }
}
