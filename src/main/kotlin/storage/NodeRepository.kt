package storage

import com.kebab.v2rayk.wrapper.config.V2rayProperties

/**
 * 节点配置持久化接口。
 * 抽象存储实现，方便后续迁移到数据库等方案。
 */
interface NodeRepository {
    /**
     * 保存节点配置到持久化存储。
     * @param group 分组名称
     * @param nodeName 节点名称
     * @param config V2rayProperties 配置对象
     * @return 配置文件的绝对路径
     */
    fun save(group: String, nodeName: String, config: V2rayProperties): String

    /**
     * 加载所有节点配置。
     * @return 分组 → [(节点名, 配置)] 的映射
     */
    fun loadAll(): Map<String, List<Pair<String, V2rayProperties>>>

    /**
     * 删除指定节点的配置。
     * @param group 分组名称
     * @param nodeName 节点名称
     */
    fun delete(group: String, nodeName: String)
}
