import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import com.kebab.v2rayk.wrapper.config.bound.ProtocolType
import settings.SettingsViewModel
import settings.SettingsWindow
import storage.JsonFileNodeRepository
import ui.ImportDialog
import java.nio.file.Paths

@Composable
fun App() {
    val repository = remember { JsonFileNodeRepository() }

    // TODO: v2ray 可执行文件路径，后续由全局设置菜单配置
    val v2rayExeName = if (System.getProperty("os.name").lowercase().contains("win")) "v2ray.exe" else "v2ray"
    val v2rayCliPath = remember {
        Paths.get(System.getProperty("user.home"), ".v2rayk", "vcore", v2rayExeName)
    }
    val connectionManager = remember { ConnectionManager(v2rayCliPath) }

    var groups by remember {
        // 启动时从存储加载已导入的节点，合并默认示例分组
        val persisted = repository.loadAll()
        val map = persisted.mapValues { (groupName, nodes) ->
            nodes.map { (name, config) ->
                ProxyNode(
                    name = name,
                    protocol = config.outbounds?.firstOrNull()?.protocol ?: ProtocolType.VMESS,
                    usedTrafficBytes = 0L,
                    configPath = "${System.getProperty("user.home")}/.v2rayk/nodes/" +
                        "${groupName.replace(Regex("[<>:\"/\\\\|?*]"), "_")}/" +
                        "${name.replace(Regex("[<>:\"/\\\\|?*]"), "_")}.json",
                )
            }
        }.toMutableMap()
        mutableStateOf(map)
    }

    var selectedNode by remember { mutableStateOf<ProxyNode?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }

    // 设置窗口状态
    val settingsViewModel = remember { SettingsViewModel() }
    var showSettings by remember { mutableStateOf(false) }

    // 错误提示对话框状态
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val connectedNode = connectionManager.connectedNode.value
    val groupNames by remember { derivedStateOf { groups.keys.toList() } }

    SingleColumnListWithMenu(
        groupItems = groups,
        selectedNode = selectedNode,
        connectedNode = connectedNode,
        onSelectNode = { node -> selectedNode = node },
        onConnectNode = { node ->
            connectionManager.switchTo(node, settingsViewModel.settings.value).onFailure { e ->
                errorMessage = e.message ?: "未知错误"
            }
        },
        onDisconnectNode = { node ->
            connectionManager.disconnect()
        },
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
        onSettings = {
            showSettings = true
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

    // 设置窗口（独立 Window）
    if (showSettings) {
        Window(
            onCloseRequest = {
                settingsViewModel.discardDraft()
                showSettings = false
            },
            title = "设置",
            resizable = true,
        ) {
            SettingsWindow(
                viewModel = settingsViewModel,
                onSave = {
                    showSettings = false
                },
                onCancel = {
                    settingsViewModel.discardDraft()
                    showSettings = false
                },
            )
        }
    }

    // 错误提示对话框
    if (errorMessage != null) {
        Dialog(onDismissRequest = { errorMessage = null }) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colors.surface,
                elevation = 8.dp
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text("连接失败")
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage!!)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { errorMessage = null },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("确定")
                    }
                }
            }
        }
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}
