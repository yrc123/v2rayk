import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.kebab.v2rayk.wrapper.V2RayCliServer
import com.kebab.v2rayk.wrapper.V2RayServer
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

    /**
     * 连接到指定节点。
     * 校验 configPath 非空、v2ray 可执行文件存在，通过后启动 V2Ray 进程。
     * 若当前已有连接，先调用 [disconnect]。
     */
    fun connect(node: ProxyNode): Result<Unit> {
        // 校验 configPath
        if (node.configPath == null) {
            return Result.failure(IllegalStateException("节点配置错误"))
        }

        // 校验 v2ray 可执行文件存在
        if (!Files.exists(v2rayCliPath)) {
            return Result.failure(
                IllegalStateException("V2Ray 核心未找到: ${v2rayCliPath.toAbsolutePath()}")
            )
        }

        return try {
            v2rayServer.start(node.configPath)
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
     * 切换到指定节点：先断开当前连接，再连接新节点。
     * @return 连接结果，断开过程不会导致失败
     */
    fun switchTo(node: ProxyNode): Result<Unit> {
        disconnect()
        return connect(node)
    }
}
