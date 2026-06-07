import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kebab.v2rayk.wrapper.config.bound.ProtocolType
import storage.JsonFileNodeRepository
import ui.ImportDialog

@Composable
fun App() {
    val repository = remember { JsonFileNodeRepository() }

    var groups by remember {
        // 启动时从存储加载已导入的节点，合并默认示例分组
        val persisted = repository.loadAll()
        val map = persisted.mapValues { (_, nodes) ->
            nodes.map { (name, config) ->
                ProxyNode(
                    name = name,
                    protocol = config.outbounds?.firstOrNull()?.protocol ?: ProtocolType.VMESS,
                    usedTrafficBytes = 0L,
                    configPath = "${System.getProperty("user.home")}/.v2rayk/nodes/" +
                        "${name.replace(Regex("[<>:\"/\\\\|?*]"), "_")}/" +
                        "${name.replace(Regex("[<>:\"/\\\\|?*]"), "_")}.json",
                )
            }
        }.toMutableMap()
        // 添加默认示例分组（仅当不存在时）
        if (!map.containsKey("香港节点")) {
            map["香港节点"] = listOf(
                ProxyNode("HK-01 IEPL 专线", ProtocolType.VMESS, 1536L),
                ProxyNode("HK-02 标准", ProtocolType.SHADOWSOCKS, 1048576L),
            )
        }
        if (!map.containsKey("日本节点")) {
            map["日本节点"] = listOf(
                ProxyNode("JP-01 东京", ProtocolType.VMESS, 1073741824L),
                ProxyNode("JP-02 大阪", ProtocolType.SOCKS, 512L),
            )
        }
        mutableStateOf(map)
    }
    var selectedNode by remember { mutableStateOf<ProxyNode?>(null) }
    var connectedNode by remember { mutableStateOf<ProxyNode?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }

    val groupNames by remember { derivedStateOf { groups.keys.toList() } }

    SingleColumnListWithMenu(
        groupItems = groups,
        selectedNode = selectedNode,
        connectedNode = connectedNode,
        onSelectNode = { node -> selectedNode = node },
        onConnectNode = { node -> connectedNode = node },
        onAddGroup = { groupName ->
            if (groupName.isNotBlank()) {
                groups = (groups + (groupName to emptyList())).toMutableMap()
            }
        },
        onGroupSettings = { groupName ->
            // 后续迭代接入 wrapper
        },
        onImportClick = {
            showImportDialog = true
        },
    )

    if (showImportDialog) {
        ImportDialog(
            groups = groupNames,
            repository = repository,
            onImportComplete = { groupName, nodeNames ->
                val existingNodes = groups[groupName] ?: emptyList()
                val newNodes = nodeNames.map { name ->
                    ProxyNode(
                        name = name,
                        protocol = ProtocolType.VMESS,
                        usedTrafficBytes = 0L,
                        configPath = "${System.getProperty("user.home")}/.v2rayk/nodes/" +
                            "${groupName.replace(Regex("[<>:\"/\\\\|?*]"), "_")}/" +
                            "${name.replace(Regex("[<>:\"/\\\\|?*]"), "_")}.json",
                    )
                }
                groups = (groups + (groupName to (existingNodes + newNodes))).toMutableMap()
            },
            onDismiss = { showImportDialog = false },
        )
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}
