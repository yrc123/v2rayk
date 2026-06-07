import com.kebab.v2rayk.wrapper.config.bound.ProtocolType

/**
 * GUI 层代理节点模型（mock 阶段）。
 * 后续接入真实配置时，可由 wrapper 的 Outbound 映射而来。
 */
data class ProxyNode(
    val name: String,            // 节点名称，列表主标题
    val protocol: ProtocolType,  // 复用 wrapper 的协议枚举
    val usedTrafficBytes: Long,  // 已用流量（字节），UI 层格式化后展示
    val configPath: String? = null,  // 导入配置的 JSON 文件路径，null 表示非导入节点
)
