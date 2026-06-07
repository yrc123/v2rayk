# 双击连接 & 右键断开连接 设计文档

- 日期: 2026-06-07
- 分支: feat/connect

## 1. 概述

实现通过节点配置启动/停止 V2Ray 的功能：
- **双击节点** → 使用该节点的 JSON 配置文件启动 V2Ray
- **右键节点** → 弹出上下文菜单，包含"连接"（未连接节点）或"断开连接"（已连接节点）

V2Ray 进程管理通过 `v2ray-core-wrapper` 库的 `V2RayCliServer` 实现。

## 2. 新增组件

### 2.1 ConnectionManager

新增 `connection/ConnectionManager.kt`，封装 V2Ray 进程管理与连接状态。

```
class ConnectionManager(v2rayCliPath: Path)
├── val connectedNode: MutableState<ProxyNode?>   // Compose 可观察状态
├── fun connect(node: ProxyNode): Result<Unit>    // 校验→启动→更新状态
├── fun disconnect()                               // 停止进程→清除状态
└── fun switchTo(node: ProxyNode): Result<Unit>   // disconnect() + connect()
```

**职责**：
- 持有 `V2RayCliServer` 实例，管理其生命周期
- `connect()` 校验 configPath 非空、v2ray 可执行文件存在，失败返回 `Result.failure`
- `switchTo()` 若已有连接先断开再连接新节点
- `disconnect()` 停止进程并置空 `connectedNode`
- `connectedNode` 为 `MutableState`，Compose 可直接观察

**不依赖 Compose UI 类型**（除 `MutableState`），可在单元测试中独立验证。

## 3. 修改文件

### 3.1 Main.kt

- 创建 `ConnectionManager` 实例，v2ray 路径默认 `~/.v2rayk/vcore/v2ray`（TODO: 后续由全局设置覆盖）
- 移除本地 `connectedNode` state，改用 `connectionManager.connectedNode`
- `onConnectNode` → 调用 `connectionManager.switchTo(node)`，按 Result 弹错误提示
- 新增 `onDisconnectNode` → 调用 `connectionManager.disconnect()`

### 3.2 SingleColumnListWithMenu.kt

- 新增参数：`onDisconnectNode: (ProxyNode) -> Unit`
- 新增参数：`onContextMenu: (ProxyNode) -> Unit`（或通过内部状态管理右键菜单弹出）
- 管理右键菜单弹出状态（`contextMenuNode`、菜单位置）
- 传递回调至 `NodeRow`

### 3.3 NodeRow.kt

- 新增参数：`onRightClick: (ProxyNode) -> Unit`
- 使用 `Modifier.pointerInput` 检测 `PointerButton.Secondary` 按键事件
- 保留现有单击/双击逻辑不变

## 4. 数据流

### 4.1 连接流程（双击节点）

```
用户双击节点
  → NodeRow 检测双击 (400ms 阈值，已有逻辑)
  → onDoubleClick(node)
  → SingleColumnListWithMenu 转发
  → App() 中 onConnectNode(node)
  → ConnectionManager.switchTo(node)
      ├─ 校验: node.configPath != null ?
      │   └─ null → Result.failure("节点配置错误")
      ├─ 校验: v2ray 可执行文件存在 ?
      │   └─ 不存在 → Result.failure("V2Ray 核心未找到")
      ├─ 若已有连接 → disconnect() 停止旧进程
      ├─ V2RayCliServer.start(node.configPath)
      └─ 更新 connectedNode = node
  → 按 Result 弹错误 Dialog
```

### 4.2 断开流程（右键 → 断开连接）

```
用户右键已连接节点 → 点击"断开连接"
  → onDisconnectNode(node)
  → ConnectionManager.disconnect()
      ├─ V2RayCliServer.stop()
      └─ connectedNode = null
```

### 4.3 右键菜单弹出

```
用户右键节点（任一节点）
  → NodeRow 检测 PointerButton.Secondary
  → 设置 contextMenuNode = node，记录点击位置
  → Popup/DropdownMenu 弹出：
      ├─ 节点未连接 → 菜单项 "连接"
      ├─ 节点已连接 → 菜单项 "断开连接"
      └─ (后续扩展：编辑、删除等)
```

## 5. 错误处理

| 场景 | 处理 |
|------|------|
| configPath 为 null | Dialog "节点配置错误，请联系管理员" |
| v2ray 可执行文件不存在 | Dialog "V2Ray 核心未找到: {路径}" |
| V2Ray 进程启动失败 | 捕获异常，Dialog "启动失败: {message}" |
| 未连接到任何节点时断开 | `disconnect()` 空操作（安全无操作） |

## 6. 待完成项（TODO）

- V2Ray 可执行文件路径：当前硬编码默认值 `~/.v2rayk/vcore/v2ray`，需后续通过全局设置菜单配置
- 右键菜单后续可扩展：编辑节点、删除节点、复制配置等选项
- 未导入的示例节点（configPath 为 null）需等后续节点编辑功能支持后才能真正连接

## 7. 文件改动清单

| 操作 | 文件 |
|------|------|
| 新增 | `src/main/kotlin/connection/ConnectionManager.kt` |
| 修改 | `src/main/kotlin/Main.kt` |
| 修改 | `src/main/kotlin/SingleColumnListWithMenu.kt` |
| 修改 | `src/main/kotlin/NodeRow.kt` |
