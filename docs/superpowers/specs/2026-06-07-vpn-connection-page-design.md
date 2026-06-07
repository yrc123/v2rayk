# VPN 连接管理页面设计

- 日期：2026-06-07
- 状态：已实现（commit 35f11a2，分支 feat/main-menu）
- 范围：纯 UI 骨架 + mock 数据（不真正启动 V2Ray 进程）

## 1. 背景与目标

当前 GUI 只有 `SingleColumnListWithMenu.kt` 一个界面，渲染硬编码的「分组 → 字符串条目」列表，数据在 `Main.kt` 里 mock。本次迭代把该页面**演进**为「VPN 连接管理页」：以分组形式展示代理节点，支持选中与连接交互。

本次**只做 UI 骨架 + mock 数据**，不调用 `v2ray-core-wrapper` 真正启动/停止 V2Ray 进程，也不实现测速、订阅、增删节点等逻辑。这些留待后续迭代。

### 成功标准

- 页面以分组形式展示代理节点，每个节点显示：名称、协议类型、已用流量。
- 已用流量按 1024 进制自动适配单位（B/KB/MB/GB/TB，保留 1 位小数）。
- 单击节点 → 选中并高亮；双击节点 → 标记为「当前连接」。
- 复用现有的分组折叠/展开、顶栏菜单、滚动条等结构。
- `./gradlew compileKotlin` 编译通过；`./gradlew run` 能看到页面并完成上述交互。

## 2. 方案选择

采用「方案 B：引入数据模型 + 抽出节点项组件」。

- 否决「方案 A（最小字符串改造）」：把节点信息塞进字符串无法干净表达协议/流量/选中状态，后续接真实逻辑要返工。
- 否决「方案 C（完全重写卡片式 + 导航）」：偏离「演进现有页面」的决定，mock 阶段属于过度设计（YAGNI）。

## 3. 跨平台与 Compose Multiplatform 规范约束

目标运行平台：**Linux、Windows、macOS** 三大桌面平台。项目已使用 JetBrains `org.jetbrains.compose` 插件（1.7.3）+ `compose.desktop.currentOs`，并配置 Dmg/Msi/Deb 打包，跨平台基础设施已就位。本次新增/改造代码须遵循：

- **不使用任何平台特定 API / 不做平台假设**：不引入 `java.awt` 平台细节、文件路径分隔符硬编码、OS 判断分支等；只用 Compose Multiplatform 公共 API。
- **主题化而非硬编码**：现有代码大量硬编码颜色（如 `Color(0xFF1976D2)`、`Color(0xFFE3F2FD)`）。新代码颜色一律取自 `MaterialTheme.colors`（选中态、连接态、分组标题背景等用主题语义色或其衍生），间距用 `dp` 常量、排版用 `MaterialTheme.typography`。
- **声明式与状态提升**：沿用现有惯用模式——状态在父级 `mutableStateOf`，事件通过 lambda 向上回调；Composable 无副作用地根据状态渲染。
- **Material 版本**：本次**保持 Material 2**（`androidx.compose.material.*`），与现有页面一致、改动最小，契合「演进现有页面」的范围。升级到 Material 3 属于独立的跨页面迁移工作，不在本次范围；本设计不阻塞后续迁移。

## 4. 数据模型（GUI 侧）

新增 GUI 层数据类（放在 `src/main/kotlin/`，如 `ProxyNode.kt`）：

```kotlin
import com.kebab.v2rayk.wrapper.config.bound.ProtocolType

data class ProxyNode(
    val name: String,            // 节点名称，列表主标题
    val protocol: ProtocolType,  // 复用 wrapper 的 ProtocolType 枚举
    val usedTrafficBytes: Long,  // 已用流量（字节），UI 层格式化后展示
)
```

- `protocol` 复用 wrapper 的 `ProtocolType`（GUI 已通过 `implementation(project(":modules:v2ray-core-wrapper"))` 依赖该模块），其 `@JsonValue` 名称（VMess/Shadowsocks/SOCKS 等）可直接用于展示，为后续接真实配置铺路。
- 分组结构沿用现有形态：`Map<String, List<ProxyNode>>`（分组名 → 节点列表）。

## 5. 状态管理

状态在 `App()`（`Main.kt`）中提升，通过参数与回调传入 `SingleColumnListWithMenu`：

- `groups: Map<String, List<ProxyNode>>` —— mock 分组数据（2~3 组，每组若干节点，协议与流量字节数写死示例值）。
- `selectedNode: ProxyNode?` —— 单击设置；用于高亮选中行。
- `connectedNode: ProxyNode?` —— 双击设置；用于显示「已连接」标记/配色。

沿用现有 Compose 惯用模式：状态在父级 `mutableStateOf`，事件通过 lambda 向上回调。节点的相等性由 `data class` 自动生成的 `equals` 决定（mock 数据保证节点名称唯一即可避免歧义）。

## 6. 组件拆分

### 6.1 `SingleColumnListWithMenu`（页面骨架，改造）

保留并继续作为页面骨架：

- 保留：顶栏 `TopAppBar` + 下拉菜单、分组折叠/展开（`groupFoldStates`）、`VerticalScrollbar`、添加分组对话框、分组设置对话框。
- 改动：函数签名由 `groupItems: Map<String, List<String>>` 改为 `groupItems: Map<String, List<ProxyNode>>`；新增 `selectedNode`、`connectedNode` 参数与 `onSelectNode`、`onConnectNode` 回调。
- 改动：分组内条目的渲染从内联的 `Box { Text(item) }` 改为调用 `NodeRow(...)`。
- 改动：硬编码颜色替换为 `MaterialTheme.colors` 语义色。
- 移除：原「双击条目弹出详情 Dialog」逻辑（`doubleClickedItem`）被「双击连接」取代。
- 顶栏标题文案更新为更贴合的名称（如「连接管理」）；下拉菜单保留现有「添加分组 / 关于」项，不在本次扩展。

### 6.2 `NodeRow`（新增节点项组件）

```kotlin
@Composable
fun NodeRow(
    node: ProxyNode,
    isSelected: Boolean,
    isConnected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
)
```

- 布局：`Row`，左侧纵向展示节点名称（主）+ 协议类型（次/较小字号）；右侧展示 `formatTraffic(node.usedTrafficBytes)`。
- 选中态：`isSelected` 为真时用主题色背景高亮。
- 连接态：`isConnected` 为真时显示标记（如左侧圆点 / 「已连接」文案或不同配色），与选中态可叠加。
- 交互：用 `pointerInput + detectTapGestures` 的 `onTap` / `onDoubleTap` 分别触发 `onClick` / `onDoubleClick`。
- 颜色全部取自 `MaterialTheme.colors`，不硬编码。

### 6.3 `formatTraffic`（新增工具函数）

```kotlin
fun formatTraffic(bytes: Long): String
```

- 按 1024 进制在 B/KB/MB/GB/TB 间自动选择单位。
- 数值保留 1 位小数；单位为 B 时不带小数（如 `512 B`）。
- 示例：`0` → `0 B`，`512` → `512 B`，`1536` → `1.5 KB`，`1048576` → `1.0 MB`。
- 负数不在 mock 数据范围内，无需特殊处理。
- 纯函数，平台无关。

## 7. 交互流程

- **单击节点** → `NodeRow.onClick` → `onSelectNode(node)` → 父级设 `selectedNode = node` → 该行高亮。
- **双击节点** → `NodeRow.onDoubleClick` → `onConnectNode(node)` → 父级设 `connectedNode = node` → 该行显示已连接标记。
- 折叠/展开、添加分组、分组设置等沿用现有行为，本次不改。

## 8. 错误处理

mock 阶段无 I/O、无进程调用，无外部错误源。`formatTraffic` 对 0 与各量级边界给出确定输出即可。后续接入 wrapper 时再处理进程启动失败、配置序列化等错误。

## 9. 测试

- 主要验证方式：`./gradlew compileKotlin` 编译通过；`./gradlew run` 手动验证分组展示、单击高亮、双击连接、流量单位显示正确，并确认无平台特定 API。
- 为纯函数 `formatTraffic` 增加轻量单元测试（边界：0 B、<1KB、KB、MB、GB、刚好 1024 进位），其逻辑确定且无 UI 依赖。
- Composable 不在本次引入 UI 测试框架。

## 10. 不在本次范围（后续迭代）

- 真正调用 `V2RayCliServer` 启动/停止 V2Ray 进程。
- 节点测速（真实 ping）、订阅导入、节点增删改。
- 协议具体配置（`Settings`/`StreamSettings`）的编辑。
- 页面导航/多页面框架。
- Material 2 → Material 3 迁移。
