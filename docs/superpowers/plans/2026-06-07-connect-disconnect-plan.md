# 双击连接 & 右键断开连接 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现双击节点启动 V2Ray 连接、右键节点弹出上下文菜单（连接/断开连接）的功能

**Architecture:** 新增 `ConnectionManager` 封装 `V2RayCliServer` 和连接状态，`App()` 持有 `ConnectionManager` 实例并通过回调传递连接/断开操作。`NodeRow` 新增右键检测，`SingleColumnListWithMenu` 管理右键弹出菜单。

**Tech Stack:** Kotlin 2.1.0, Compose Desktop 1.7.3, v2ray-core-wrapper, Jackson, Java NIO Path/Files

---

### Task 1: 创建 ConnectionManager

**Files:**
- Create: `src/main/kotlin/connection/ConnectionManager.kt`

- [ ] **Step 1: 创建文件及 ConnectionManager 类骨架**

```kotlin
package connection

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
```

- [ ] **Step 2: 验证文件编译通过**

```bash
cd v2rayk && ./gradlew :modules:v2ray-core-wrapper:compileKotlin compileKotlin
```

预期: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add src/main/kotlin/connection/ConnectionManager.kt
git commit -m "feat: add ConnectionManager to wrap V2Ray process lifecycle
Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: 修改 NodeRow — 新增右键检测

**Files:**
- Modify: `src/main/kotlin/NodeRow.kt`

- [ ] **Step 1: 添加 `onRightClick` 参数和右键检测逻辑**

修改 `NodeRow.kt`，在现有 `clickable` 基础上增加 `pointerInput` 检测右键点击：

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * 单个节点项：左侧名称(主) + 协议(次)，右侧已用流量。
 * 选中态/连接态配色全部取自 MaterialTheme.colors，可叠加。
 */
@Composable
fun NodeRow(
    node: ProxyNode,
    isSelected: Boolean,
    isConnected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onRightClick: () -> Unit = {},  // ← 新增参数，默认为空操作
) {
    var lastClickTime by remember { mutableStateOf(0L) }

    val background = when {
        isConnected -> MaterialTheme.colors.primary.copy(alpha = 0.18f)
        isSelected -> MaterialTheme.colors.primary.copy(alpha = 0.08f)
        else -> MaterialTheme.colors.surface
    }
    val borderColor = if (isSelected || isConnected) {
        MaterialTheme.colors.primary
    } else {
        MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .pointerInput(node) {  // ← 新增：检测右键
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press &&
                            event.changes.any { it.button == PointerButton.Secondary }
                        ) {
                            event.changes.forEach { it.consume() }
                            onRightClick()
                        }
                    }
                }
            }
            .clickable {
                val now = System.currentTimeMillis()
                if (now - lastClickTime < 400) {
                    onDoubleClick()
                    lastClickTime = 0L
                } else {
                    onClick()
                    lastClickTime = now
                }
            }
            .padding(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(node.name, style = MaterialTheme.typography.subtitle1)
            Text(
                node.protocol.protocolName,
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
            )
        }
        if (isConnected) {
            Text(
                "已连接",
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.primary,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            formatTraffic(node.usedTrafficBytes),
            style = MaterialTheme.typography.body2,
        )
    }
}
```

> **变更要点**：
> - 新增 `import`s: `PointerButton`, `PointerEventPass`, `PointerEventType`, `pointerInput`
> - 新增参数 `onRightClick: () -> Unit = {}`，默认空操作保证向后兼容
> - 在 `clickable` 之前添加 `pointerInput` 块，以 `PointerEventPass.Initial` 拦截右键事件并消费，左键事件继续传递给 `clickable`

- [ ] **Step 2: 编译验证**

```bash
cd v2rayk && ./gradlew compileKotlin
```

预期: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add src/main/kotlin/NodeRow.kt
git commit -m "feat: add right-click detection to NodeRow via pointerInput
Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: 修改 SingleColumnListWithMenu — 添加右键菜单和断开回调

**Files:**
- Modify: `src/main/kotlin/SingleColumnListWithMenu.kt`

- [ ] **Step 1: 引入需要的新 import，新增参数和右键菜单逻辑**

完整替换 `SingleColumnListWithMenu.kt`：

```kotlin
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown

@Composable
fun SingleColumnListWithMenu(
    groupItems: Map<String, List<ProxyNode>>,
    selectedNode: ProxyNode?,
    connectedNode: ProxyNode?,
    onSelectNode: (ProxyNode) -> Unit,
    onConnectNode: (ProxyNode) -> Unit,
    onDisconnectNode: (ProxyNode) -> Unit,  // ← 新增参数
    onAddGroup: (String) -> Unit,
    onGroupSettings: (String) -> Unit,
    onImportClick: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var settingGroupName by remember { mutableStateOf<String?>(null) }
    // 保存每个分组的折叠状态（true=折叠，false=展开，默认展开）
    val groupFoldStates = remember { mutableStateMapOf<String, Boolean>() }

    // 保证新加分组有默认展开状态
    LaunchedEffect(groupItems.keys) {
        groupItems.keys.forEach { key ->
            if (groupFoldStates[key] == null) groupFoldStates[key] = false
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("连接管理") },
                    actions = {
                        IconButton(onClick = onImportClick) {
                            Text("导入")
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Text("选项")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(onClick = {
                                    menuExpanded = false
                                    showAddGroupDialog = true
                                }) {
                                    Text("添加分组")
                                }
                                DropdownMenuItem(onClick = {
                                    menuExpanded = false
                                }) {
                                    Text("关于")
                                }
                            }
                        }
                    },
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = MaterialTheme.colors.onPrimary
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .border(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.12f))
            ) {
                Row {
                    val state = rememberLazyListState()
                    LazyColumn(
                        state = state,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        groupItems.forEach { (groupName, nodes) ->
                            val isFolded = groupFoldStates[groupName] ?: false
                            // 分组标题行
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colors.primary.copy(alpha = 0.1f))
                                        .padding(vertical = 8.dp, horizontal = 8.dp)
                                ) {
                                    // 折叠/展开按钮
                                    IconButton(
                                        onClick = {
                                            groupFoldStates[groupName] = !isFolded
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            // 横向为折叠(❯)，下向为展开(▼)
                                            imageVector = if (isFolded)
                                                Icons.Default.KeyboardArrowRight
                                            else
                                                Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isFolded) "展开" else "折叠"
                                        )
                                    }
                                    Text(groupName, style = MaterialTheme.typography.subtitle1)
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { settingGroupName = groupName }) {
                                        Icon(Icons.Default.Settings, contentDescription = "设置")
                                    }
                                }
                            }
                            // 分组内节点（未折叠时显示）
                            if (!isFolded) {
                                items(nodes) { node ->
                                    // ← 新增：用 Box 包裹 NodeRow + DropdownMenu
                                    var showContextMenu by remember { mutableStateOf(false) }
                                    Box {
                                        NodeRow(
                                            node = node,
                                            isSelected = node == selectedNode,
                                            isConnected = node == connectedNode,
                                            onClick = { onSelectNode(node) },
                                            onDoubleClick = { onConnectNode(node) },
                                            onRightClick = { showContextMenu = true },
                                        )
                                        DropdownMenu(
                                            expanded = showContextMenu,
                                            onDismissRequest = { showContextMenu = false }
                                        ) {
                                            if (node == connectedNode) {
                                                DropdownMenuItem(onClick = {
                                                    showContextMenu = false
                                                    onDisconnectNode(node)
                                                }) {
                                                    Text("断开连接")
                                                }
                                            } else {
                                                DropdownMenuItem(onClick = {
                                                    showContextMenu = false
                                                    onConnectNode(node)
                                                }) {
                                                    Text("连接")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(state),
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(start = 4.dp)
                    )
                }

                // 添加分组对话框
                if (showAddGroupDialog) {
                    Dialog(onDismissRequest = { showAddGroupDialog = false }) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colors.surface,
                            elevation = 8.dp
                        ) {
                            Column(Modifier.padding(24.dp)) {
                                Text("新分组名称")
                                OutlinedTextField(
                                    value = newGroupName,
                                    onValueChange = { newGroupName = it }
                                )
                                Row(Modifier.padding(top = 16.dp)) {
                                    Button(
                                        onClick = {
                                            onAddGroup(newGroupName)
                                            newGroupName = ""
                                            showAddGroupDialog = false
                                        },
                                        enabled = newGroupName.isNotBlank()
                                    ) {
                                        Text("添加")
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Button(onClick = { showAddGroupDialog = false }) {
                                        Text("取消")
                                    }
                                }
                            }
                        }
                    }
                }
                // 分组设置对话框
                settingGroupName?.let { group ->
                    Dialog(onDismissRequest = { settingGroupName = null }) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colors.surface,
                            elevation = 8.dp
                        ) {
                            Box(Modifier.padding(24.dp)) {
                                Column {
                                    Text("设置分组: $group")
                                    Button(
                                        onClick = {
                                            onGroupSettings(group)
                                            settingGroupName = null
                                        },
                                        Modifier.padding(top = 16.dp)
                                    ) {
                                        Text("保存设置")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
```

> **变更要点**：
> - 新增参数 `onDisconnectNode: (ProxyNode) -> Unit`
> - 每个 `NodeRow` 外层包裹 `Box`，包含 `DropdownMenu`
> - 每个节点的 `showContextMenu` 状态独立管理
> - 右键菜单项根据 `node == connectedNode` 显示"断开连接"或"连接"

- [ ] **Step 2: 编译验证**

```bash
cd v2rayk && ./gradlew compileKotlin
```

预期: BUILD SUCCESSFUL（此时 Main.kt 会因缺少 `onDisconnectNode` 参数而编译失败，这是预期行为，Task 4 会修复）

- [ ] **Step 3: 暂不提交（与 Task 4 一起提交，Main.kt 修改后会修复编译错误）**

---

### Task 4: 修改 Main.kt — 集成 ConnectionManager

**Files:**
- Modify: `src/main/kotlin/Main.kt`

- [ ] **Step 1: 重写 Main.kt 接入 ConnectionManager**

```kotlin
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import com.kebab.v2rayk.wrapper.config.bound.ProtocolType
import connection.ConnectionManager
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
    var showImportDialog by remember { mutableStateOf(false) }

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
            connectionManager.switchTo(node).onFailure { e ->
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
```

> **变更要点**：
> - `import connection.ConnectionManager`, `import java.nio.file.Paths`, `import` 若干 Compose 组件
> - 创建 `ConnectionManager` 实例，v2ray 路径默认 `~/.v2rayk/vcore/v2ray(.exe)`
> - `connectedNode` 来源于 `connectionManager.connectedNode.value`（单向读取）
> - `onConnectNode` 调用 `connectionManager.switchTo(node)`，失败设置 `errorMessage`
> - 新增 `onDisconnectNode` 回调调用 `connectionManager.disconnect()`
> - 新增错误提示 `Dialog`，显示连接失败信息

- [ ] **Step 2: 编译验证**

```bash
cd v2rayk && ./gradlew compileKotlin
```

预期: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add src/main/kotlin/SingleColumnListWithMenu.kt src/main/kotlin/Main.kt
git commit -m "feat: integrate ConnectionManager, add right-click context menu with connect/disconnect
Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: 完整构建验证

- [ ] **Step 1: 完整构建**

```bash
cd v2rayk && ./gradlew build
```

预期: BUILD SUCCESSFUL（所有模块编译通过）

- [ ] **Step 2: 最终提交（如有遗漏文件）**

```bash
git status
# 确认所有变更已提交
```
