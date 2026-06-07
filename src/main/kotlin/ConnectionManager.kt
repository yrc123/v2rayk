import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.kebab.v2rayk.wrapper.V2RayCliServer
import com.kebab.v2rayk.wrapper.V2RayServer
import com.kebab.v2rayk.wrapper.config.Inbound
import com.kebab.v2rayk.wrapper.config.V2rayProperties
import com.kebab.v2rayk.wrapper.config.bound.ProtocolType
import com.kebab.v2rayk.wrapper.config.bound.settings.HttpAccount
import com.kebab.v2rayk.wrapper.config.bound.settings.HttpInboundSettings
import com.kebab.v2rayk.wrapper.config.bound.settings.SocksAccount
import com.kebab.v2rayk.wrapper.config.bound.settings.SocksAuth
import com.kebab.v2rayk.wrapper.config.bound.settings.SocksInboundSettings
import settings.model.GlobalSettings
import java.nio.file.Files
import java.nio.file.Path

/**
 * V2Ray 连接管理器，封装 [V2RayServer] 的进程生命周期与 Compose 可观察的连接状态。
 *
 * @param v2rayCliPath V2Ray 可执行文件的路径
 */
class ConnectionManager(
    private val v2rayCliPath: Path,
) {
    private val v2rayServer: V2RayServer

    /** 当前连接的节点，null 表示未连接。Compose 可直接观察此状态。 */
    val connectedNode: MutableState<ProxyNode?> = mutableStateOf(null)

    init {
        require(v2rayCliPath.toString().isNotBlank()) { "V2Ray CLI path cannot be empty" }
        v2rayServer = V2RayCliServer(v2rayCliPath)
    }

    private val configMapper = ObjectMapper().registerKotlinModule()

    /**
     * 连接到指定节点，合并全局入站设置到节点配置。
     * 若当前已有连接，先调用 [disconnect]。
     */
    fun connect(node: ProxyNode, globalSettings: GlobalSettings): Result<Unit> {
        if (node.configPath == null) {
            return Result.failure(IllegalStateException("节点配置错误"))
        }

        if (!Files.exists(v2rayCliPath)) {
            return Result.failure(
                IllegalStateException("V2Ray 核心未找到: ${v2rayCliPath.toAbsolutePath()}")
            )
        }

        return try {
            // 读取节点配置 JSON 并合并全局入站
            val configFile = Path.of(node.configPath).toFile()
            val nodeConfig: V2rayProperties = if (configFile.exists()) {
                configMapper.readValue(configFile)
            } else {
                V2rayProperties()
            }

            val mergedConfig = mergeInboundSettings(nodeConfig, globalSettings)

            // 将合并后的配置写入临时文件用于启动
            val tempConfigFile = Files.createTempFile("v2rayk-merged-", ".json")
            configMapper.writerWithDefaultPrettyPrinter().writeValue(tempConfigFile.toFile(), mergedConfig)
            tempConfigFile.toFile().deleteOnExit()

            v2rayServer.start(tempConfigFile.toAbsolutePath().toString())
            connectedNode.value = node
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 断开当前连接，停止 V2Ray 进程并清除连接状态。
     * 若未连接则安全无操作。
     */
    fun disconnect() {
        v2rayServer.stop()
        connectedNode.value = null
    }

    /**
     * 切换到指定节点：先断开当前连接，再连接新节点并合并全局入站设置。
     * @return 连接结果，断开过程不会导致失败
     */
    fun switchTo(node: ProxyNode, globalSettings: GlobalSettings): Result<Unit> {
        disconnect()
        return connect(node, globalSettings)
    }

    /**
     * 将全局入站设置生成为 SOCKS + HTTP 入站，与节点配置的 inbounds 合并。
     * 若节点已有同名 tag（如 "socks-in", "http-in"），则覆盖其 port/listen/settings。
     */
    private fun mergeInboundSettings(
        nodeConfig: V2rayProperties,
        globalSettings: GlobalSettings,
    ): V2rayProperties {
        val inbound = globalSettings.inbound

        val socksInbound = Inbound(
            port = inbound.socksPort.toString(),
            listen = inbound.listen,
            protocol = ProtocolType.SOCKS,
            settings = SocksInboundSettings(
                udp = inbound.socksUdpEnabled,
                auth = if (inbound.socksAuth != null) SocksAuth.PASSWORD else SocksAuth.NOAUTH,
                accounts = inbound.socksAuth?.let {
                    listOf(SocksAccount(user = it.user, pass = it.pass))
                } ?: emptyList(),
            ),
            tag = "socks-in",
        )

        val httpInbound = Inbound(
            port = inbound.httpPort.toString(),
            listen = inbound.listen,
            protocol = ProtocolType.HTTP,
            settings = HttpInboundSettings(
                accounts = inbound.httpAuth?.let {
                    listOf(HttpAccount(user = it.user, pass = it.pass))
                } ?: emptyList(),
            ),
            tag = "http-in",
        )

        // 用全局生成的入站替换/追加到节点配置
        val existingInbounds = nodeConfig.inbounds?.toMutableList() ?: mutableListOf()
        val socksIndex = existingInbounds.indexOfFirst { it.tag == "socks-in" }
        if (socksIndex >= 0) existingInbounds[socksIndex] = socksInbound
        else existingInbounds.add(socksInbound)

        val httpIndex = existingInbounds.indexOfFirst { it.tag == "http-in" }
        if (httpIndex >= 0) existingInbounds[httpIndex] = httpInbound
        else existingInbounds.add(httpInbound)

        return nodeConfig.copy(inbounds = existingInbounds)
    }
}
