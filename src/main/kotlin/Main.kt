import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kebab.v2rayk.wrapper.config.bound.ProtocolType

@Composable
fun App() {
    var groups by remember {
        mutableStateOf(
            mapOf(
                "香港节点" to listOf(
                    ProxyNode("HK-01 IEPL 专线", ProtocolType.VMESS, 1536L),
                    ProxyNode("HK-02 标准", ProtocolType.SHADOWSOCKS, 1048576L),
                ),
                "日本节点" to listOf(
                    ProxyNode("JP-01 东京", ProtocolType.VMESS, 1073741824L),
                    ProxyNode("JP-02 大阪", ProtocolType.SOCKS, 512L),
                ),
            )
        )
    }
    var selectedNode by remember { mutableStateOf<ProxyNode?>(null) }
    var connectedNode by remember { mutableStateOf<ProxyNode?>(null) }

    SingleColumnListWithMenu(
        groupItems = groups,
        selectedNode = selectedNode,
        connectedNode = connectedNode,
        onSelectNode = { node -> selectedNode = node },
        onConnectNode = { node -> connectedNode = node },
        onAddGroup = { groupName ->
            if (groupName.isNotBlank()) {
                groups = groups + (groupName to emptyList())
            }
        },
        onGroupSettings = { groupName ->
            // 这里可以执行具体分组设置逻辑（后续迭代接入 wrapper）
        }
    )
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}
