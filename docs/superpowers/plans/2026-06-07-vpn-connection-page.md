# VPN 连接管理页面 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把现有 `SingleColumnListWithMenu.kt` 演进为「VPN 连接管理页」——以分组形式展示代理节点（名称/协议/已用流量），支持单击选中高亮、双击标记为当前连接，纯 UI 骨架 + mock 数据。

**Architecture:** 新增 GUI 层数据模型 `ProxyNode` 与纯函数 `formatTraffic`，抽出 `NodeRow` 节点项组件；改造 `SingleColumnListWithMenu` 的函数签名与渲染，状态在 `Main.kt` 的 `App()` 中提升、通过 lambda 回调。颜色全部取自 `MaterialTheme.colors`，保持 Material 2，不使用任何平台特定 API。

**Tech Stack:** Kotlin 2.1.0、JetBrains Compose Desktop 1.7.3（Material 2）、Gradle 8.7、JVM 21、kotlin-test（新增，仅测纯函数）。

**Spec:** `docs/superpowers/specs/2026-06-07-vpn-connection-page-design.md`

---

## File Structure

| 文件 | 职责 | 动作 |
|------|------|------|
| `src/main/kotlin/ProxyNode.kt` | GUI 层节点数据模型 | 新建 |
| `src/main/kotlin/TrafficFormat.kt` | `formatTraffic` 纯函数（字节→人类可读单位） | 新建 |
| `src/main/kotlin/NodeRow.kt` | 单个节点项 Composable | 新建 |
| `src/main/kotlin/SingleColumnListWithMenu.kt` | 页面骨架（签名/渲染/配色改造） | 修改 |
| `src/main/kotlin/Main.kt` | mock 数据 + 状态提升 | 修改 |
| `src/test/kotlin/TrafficFormatTest.kt` | `formatTraffic` 边界单元测试 | 新建 |
| `build.gradle.kts` | 新增 test 依赖与 useJUnitPlatform | 修改 |

设计原则：每个文件单一职责。数据模型、纯函数、UI 组件、页面骨架分离，便于独立理解与测试。

---

## Task 1: 数据模型 ProxyNode

**Files:**
- Create: `src/main/kotlin/ProxyNode.kt`

- [ ] **Step 1: 创建数据模型文件**

`src/main/kotlin/ProxyNode.kt`（默认包，与 `Main.kt` 一致，无 `package` 声明）：

```kotlin
import com.kebab.v2rayk.wrapper.config.bound.ProtocolType

/**
 * GUI 层代理节点模型（mock 阶段）。
 * 后续接入真实配置时，可由 wrapper 的 Outbound 映射而来。
 */
data class ProxyNode(
    val name: String,            // 节点名称，列表主标题
    val protocol: ProtocolType,  // 复用 wrapper 的协议枚举
    val usedTrafficBytes: Long,  // 已用流量（字节），UI 层格式化后展示
)
```

> 说明：`ProtocolType` 位于 `com.kebab.v2rayk.wrapper.config.bound`，已通过 `implementation(project(":modules:v2ray-core-wrapper"))` 可用。其展示名通过 `protocol.protocolName` 字段获取（如 `ProtocolType.VMESS.protocolName == "VMess"`）。

- [ ] **Step 2: 编译验证**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL（`ProxyNode` 解析到 `ProtocolType` 无报错）。

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/ProxyNode.kt
git commit -m "feat: 添加 GUI 层 ProxyNode 数据模型"
```

---

## Task 2: formatTraffic 纯函数（TDD）

**Files:**
- Modify: `build.gradle.kts`
- Create: `src/test/kotlin/TrafficFormatTest.kt`
- Create: `src/main/kotlin/TrafficFormat.kt`

- [ ] **Step 1: 添加测试依赖**

修改 `build.gradle.kts`，在 `dependencies { ... }` 块内 `implementation(project(...))` 之后追加一行：

```kotlin
    testImplementation(kotlin("test"))
```

并在文件末尾（`compose.desktop { ... }` 块之后）追加：

```kotlin
tasks.test {
    useJUnitPlatform()
}
```

修改后 `dependencies` 块完整为：

```kotlin
dependencies {
    // Note, if you develop a library, you should use compose.desktop.common.
    // compose.desktop.currentOs should be used in launcher-sourceSet
    // (in a separate module for demo project and in testMain).
    // With compose.desktop.common you will also lose @Preview functionality
    implementation(compose.desktop.currentOs)
    implementation(project(":modules:v2ray-core-wrapper"))
    testImplementation(kotlin("test"))
}
```

- [ ] **Step 2: 写失败的测试**

`src/test/kotlin/TrafficFormatTest.kt`（默认包）：

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals

class TrafficFormatTest {

    @Test
    fun zeroBytes_showsBytesWithoutDecimal() {
        assertEquals("0 B", formatTraffic(0))
    }

    @Test
    fun underOneKilobyte_showsBytesWithoutDecimal() {
        assertEquals("512 B", formatTraffic(512))
    }

    @Test
    fun exactlyOneKilobyte_promotesToKB() {
        assertEquals("1.0 KB", formatTraffic(1024))
    }

    @Test
    fun kilobytes_oneDecimal() {
        assertEquals("1.5 KB", formatTraffic(1536))
    }

    @Test
    fun exactlyOneMegabyte() {
        assertEquals("1.0 MB", formatTraffic(1048576))
    }

    @Test
    fun exactlyOneGigabyte() {
        assertEquals("1.0 GB", formatTraffic(1073741824))
    }
}
```

- [ ] **Step 3: 运行测试，确认编译失败（函数未定义）**

Run: `./gradlew test --tests "TrafficFormatTest"`
Expected: FAIL —— 编译错误 "unresolved reference: formatTraffic"。

- [ ] **Step 4: 实现纯函数**

`src/main/kotlin/TrafficFormat.kt`（默认包）：

```kotlin
import java.util.Locale

/**
 * 按 1024 进制把字节数格式化为 B/KB/MB/GB/TB。
 * - 小于 1024 字节：整数 + "B"，不带小数（如 "512 B"）。
 * - 其余：保留 1 位小数（如 "1.5 KB"、"1.0 MB"）。
 * 使用 Locale.ROOT 保证不同区域设置下输出一致（跨平台确定性），纯函数、无 OS 假设。
 */
fun formatTraffic(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.ROOT, "%.1f %s", value, units[unitIndex])
}
```

- [ ] **Step 5: 运行测试，确认通过**

Run: `./gradlew test --tests "TrafficFormatTest"`
Expected: PASS —— 6 个测试全部通过。

- [ ] **Step 6: Commit**

```bash
git add build.gradle.kts src/main/kotlin/TrafficFormat.kt src/test/kotlin/TrafficFormatTest.kt
git commit -m "feat: 添加 formatTraffic 流量格式化纯函数及单元测试"
```

---

## Task 3: NodeRow 节点项组件

**Files:**
- Create: `src/main/kotlin/NodeRow.kt`

- [ ] **Step 1: 创建组件文件**

`src/main/kotlin/NodeRow.kt`（默认包）：

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
) {
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
            .padding(12.dp)
            .pointerInput(node) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = { onDoubleClick() },
                )
            },
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

> `Modifier.weight(1f)` 在 `RowScope` 内可用（`Column` 是 `Row` 的子项），无需额外导入。

- [ ] **Step 2: 编译验证**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL（`NodeRow` 引用 `ProxyNode`、`formatTraffic` 均已存在）。

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/NodeRow.kt
git commit -m "feat: 添加 NodeRow 节点项组件"
```

---

## Task 4: 改造 SingleColumnListWithMenu 页面骨架

**Files:**
- Modify: `src/main/kotlin/SingleColumnListWithMenu.kt`

改动要点：签名换为 `ProxyNode` 并新增选中/连接参数与回调；条目渲染改用 `NodeRow`；移除原「双击弹详情」逻辑；硬编码颜色替换为主题色；标题改「连接管理」。

- [ ] **Step 1: 用以下完整内容替换整个文件**

`src/main/kotlin/SingleColumnListWithMenu.kt`：

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
    onAddGroup: (String) -> Unit,
    onGroupSettings: (String) -> Unit,
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
                                    NodeRow(
                                        node = node,
                                        isSelected = node == selectedNode,
                                        isConnected = node == connectedNode,
                                        onClick = { onSelectNode(node) },
                                        onDoubleClick = { onConnectNode(node) },
                                    )
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

> 相对原文件的变化：①签名换 `ProxyNode` + 新增 4 个选中/连接参数与回调；②删除 `doubleClickedItem` 状态与其详情 Dialog；③内联 `Box { Text(item) }` 改为 `NodeRow(...)`；④移除未使用的导入（`Color`、`detectTapGestures`、`pointerInput`、`RoundedCornerShape` 仍被对话框使用故保留）；⑤TopAppBar/分组标题/对话框配色改主题色；⑥标题「我的菜单栏」→「连接管理」。

- [ ] **Step 2: 编译验证**

Run: `./gradlew compileKotlin`
Expected: 此时 `Main.kt` 仍在用旧签名，会编译失败 —— 这是预期的，Task 5 修复 `Main.kt`。仅确认 `SingleColumnListWithMenu.kt` 本身无语法/引用错误（错误应只出现在 `Main.kt` 的调用处）。

- [ ] **Step 3: Commit（与 Task 5 一起提交，避免中间不可编译状态）**

本任务先不单独提交，待 Task 5 完成后一并提交。

---

## Task 5: Main.kt mock 数据与状态提升

**Files:**
- Modify: `src/main/kotlin/Main.kt`

- [ ] **Step 1: 用以下完整内容替换整个文件**

`src/main/kotlin/Main.kt`：

```kotlin
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
```

> 变化：①移除未使用的 `Button`/`MaterialTheme`/`Text`/`V2RayCliServer`/`Path` 导入，新增 `ProtocolType` 导入；②mock 数据由 `List<String>` 改为 `List<ProxyNode>`，每组含示例协议与流量字节；③新增 `selectedNode`/`connectedNode` 状态与对应回调。

- [ ] **Step 2: 编译验证**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL（`Main.kt` 与改造后的 `SingleColumnListWithMenu` 签名匹配）。

- [ ] **Step 3: Commit（Task 4 + Task 5 一起）**

```bash
git add src/main/kotlin/SingleColumnListWithMenu.kt src/main/kotlin/Main.kt
git commit -m "feat: 演进为 VPN 连接管理页（分组节点、单击选中、双击连接）"
```

---

## Task 6: 整体编译与手动验证

**Files:** 无（验证任务）

- [ ] **Step 1: 全量编译**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 2: 跑测试**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL，`TrafficFormatTest` 6 个用例通过。

- [ ] **Step 3: 运行应用手动验证**

Run: `./gradlew run`
逐项确认：
- 顶栏标题为「连接管理」，配色取自主题色。
- 两个分组（香港/日本）各显示节点：名称（主）+ 协议名（次，如 VMess/Shadowsocks/SOCKS）+ 右侧流量（`1.5 KB`/`1.0 MB`/`1.0 GB`/`512 B`）。
- 单击某节点 → 该行高亮（选中态背景/边框）。
- 双击某节点 → 该行出现「已连接」标记与连接态配色；再单击另一节点，选中态与连接态可并存。
- 折叠/展开分组、「添加分组」对话框、分组设置齿轮对话框均工作正常。
- 整体无平台特定 API（视觉上一致，无报错）。

- [ ] **Step 4: 标记 spec 状态完成（可选）**

将 `docs/superpowers/specs/2026-06-07-vpn-connection-page-design.md` 顶部状态由「已批准设计，待实现」更新为「已实现」。

```bash
git add docs/superpowers/specs/2026-06-07-vpn-connection-page-design.md
git commit -m "docs: 标记 VPN 连接页设计为已实现"
```

---

## Self-Review 记录

- **Spec coverage**：§4 数据模型→Task 1；§6.3 formatTraffic→Task 2；§6.2 NodeRow→Task 3；§6.1 页面骨架改造（签名/渲染/配色/移除双击详情/标题）→Task 4；§5 状态提升 + mock→Task 5；§9 测试（formatTraffic 单测 + run 手动验证）→Task 2 & Task 6。§3 跨平台约束（主题色、无平台 API、Material 2、状态提升）贯穿 Task 3/4/5。§10 不在范围项未实现 ✓。
- **Placeholder scan**：无 TBD/TODO（Main.kt 中 `onGroupSettings` 的注释为沿用原有占位回调，属设计明确的「本次不扩展」，非计划占位）。
- **Type consistency**：`ProxyNode(name, protocol, usedTrafficBytes)`、`formatTraffic(Long): String`、`NodeRow(node, isSelected, isConnected, onClick, onDoubleClick)`、`SingleColumnListWithMenu(groupItems, selectedNode, connectedNode, onSelectNode, onConnectNode, onAddGroup, onGroupSettings)` 在各 Task 间一致；`protocol.protocolName` 字段名与 wrapper `ProtocolType` 一致。
- **编译顺序**：Task 4 单独编译会因 Main.kt 旧签名失败，故 Task 4/5 合并提交，避免中间不可编译的提交。