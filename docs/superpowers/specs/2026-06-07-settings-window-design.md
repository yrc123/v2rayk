# 设置弹窗（独立 Window + Tab 架构）设计文档

- 日期: 2026-06-07
- 分支: feat/connect

## 1. 概述

将 TopAppBar 中的"选项"下拉菜单改为"设置"按钮，点击后打开独立设置窗口。窗口采用 Tab 架构组织配置，首期实现"入站设置"和"关于"两个 Tab，其余 Tab 为占位。

原"选项"菜单中的"添加分组"移至分组列表底部，"关于"迁入设置窗口的关于 Tab。

## 2. 架构设计

### 2.1 组件分层

```
src/main/kotlin/
├── settings/
│   ├── SettingsWindow.kt        ← 设置窗口宿主（独立 Window + TabRow）
│   ├── SettingsViewModel.kt     ← 全局设置状态持有者
│   ├── tabs/
│   │   ├── InboundSettingsTab.kt   ← 入站设置表单
│   │   ├── OutboundSettingsTab.kt  ← 占位（即将推出）
│   │   ├── RoutingTab.kt           ← 占位（即将推出）
│   │   ├── DnsTab.kt               ← 占位（即将推出）
│   │   └── AboutTab.kt             ← 关于信息
│   └── model/
│       └── GlobalSettings.kt    ← 全局设置 data class
```

### 2.2 修改文件

| 文件 | 变更 |
|------|------|
| `Main.kt` | "选项"按钮→"设置"按钮；创建 `SettingsViewModel`；条件渲染独立 `Window`；`switchTo` 传入全局设置 |
| `SingleColumnListWithMenu.kt` | 移除"添加分组"菜单项；分组列表底部新增"+ 添加分组"按钮；移除"关于"菜单项；新增 `onSettings` 回调 |
| `connection/ConnectionManager.kt` | `switchTo()` 签名新增 `GlobalSettings` 参数，动态生成 SOCKS + HTTP 入站合并到节点配置 |

## 3. 数据模型

### 3.1 GlobalSettings

```kotlin
data class GlobalSettings(
    val inbound: InboundGlobalConfig = InboundGlobalConfig(),
)

data class InboundGlobalConfig(
    val listen: String = "0.0.0.0",
    val socksPort: Int = 10808,
    val socksUdpEnabled: Boolean = false,
    val socksAuth: SocksAuthConfig? = null,
    val httpPort: Int = 10809,
    val httpAuth: HttpAuthConfig? = null,
)

data class SocksAuthConfig(
    val user: String = "",
    val pass: String = "",
)

data class HttpAuthConfig(
    val user: String = "",
    val pass: String = "",
)
```

### 3.2 SettingsViewModel

```kotlin
class SettingsViewModel {
    private val _settings = MutableStateFlow(GlobalSettings())
    val settings: StateFlow<GlobalSettings>

    private val _draft = MutableStateFlow(GlobalSettings())
    val draft: StateFlow<GlobalSettings>

    fun load()                       // 从 ~/.v2rayk/settings.json 加载
    fun save()                       // 将 draft 提交到 _settings 并持久化
    fun updateDraft(transform: (GlobalSettings) -> GlobalSettings)
    fun discardDraft()               // 放弃修改，draft 重置为 settings
}
```

**关键设计：`settings` 与 `draft` 分离**

- `settings` 是已保存的持久配置（真实值）
- `draft` 是设置窗口中的编辑副本
- 点击"取消"或关闭窗口 → `discardDraft()`，真实配置不受影响
- 点击"保存" → `save()`，draft 覆写到 settings 并持久化 JSON
- 外部消费者（如 `ConnectionManager`）始终读取 `settings`

### 3.3 持久化

- 路径：`~/.v2rayk/settings.json`
- 格式：Jackson 序列化 `GlobalSettings`
- 首次启动文件不存在 → 使用默认值
- 文件损坏 → 使用默认值 + 警告提示

### 3.4 数据流

```
InboundSettingsTab (UI 表单)
  └─ 修改 draft ←── viewModel.updateDraft { ... }
  └─ 点"保存" → viewModel.save() → 写 JSON 文件

ConnectionManager.switchTo(node, globalSettings)
  └─ 读取 globalSettings.inbound
  └─ 生成 Inbound(SOCKS) + Inbound(HTTP)
  └─ 合并到节点 V2rayProperties.inbounds
  └─ 启动 V2Ray 进程
```

## 4. UI 布局

### 4.1 设置窗口整体

```
┌─────────────────────────────────────────┐
│  设置                          ✕        │
├─────────────────────────────────────────┤
│  [入站设置] [出站设置] [路由] [DNS] [关于] │  ← ScrollableTabRow
├─────────────────────────────────────────┤
│                                         │
│  监听地址: [  0.0.0.0            ]      │
│                                         │
│  ─── SOCKS ──────────────────────────   │
│  端口:     [  10808              ]      │
│  UDP:      [ ✓ ]                       │
│  认证:     [ ✓ ]  用户: [...] 密码: [...] │
│                                         │
│  ─── HTTP ───────────────────────────   │
│  端口:     [  10809              ]      │
│  认证:     [ ✓ ]  用户: [...] 密码: [...] │
│                                         │
├─────────────────────────────────────────┤
│                    [取消]    [保存]      │
└─────────────────────────────────────────┘
```

### 4.2 布局要点

- **窗口类型**：Compose Desktop `Window`（独立窗口，可拖出主窗口、可最小化）
- **窗口尺寸**：最小 480×420 dp，`resizable = true`
- **关闭行为**：✕ / Alt+F4 → 丢弃草稿并关闭
- **TabRow**：`ScrollableTabRow`，5 个 Tab；不可用 Tab 灰色显示
- **表单区**：`Column` + `VerticalScrollbar`，内容可滚动
- **分组线**：SOCKS 和 HTTP 用 `HorizontalDivider` + 小节标题区隔
- **认证区**：`Checkbox` 控制 `AnimatedVisibility` 展开/收起用户密码字段
- **底部按钮栏**：`Row(Arrangement.End)`，"取消"+"保存"
- **占位 Tab**：居中显示"即将推出"文字

### 4.3 入站设置表单字段

| 字段 | 组件 | 默认值 | 说明 |
|------|------|--------|------|
| 监听地址 | `OutlinedTextField` | `0.0.0.0` | 绑定所有网卡 |
| SOCKS 端口 | `OutlinedTextField`（数字） | `10808` | V2Ray 默认 SOCKS 端口 |
| SOCKS UDP | `Checkbox` / `Switch` | 关闭 | 是否支持 UDP 转发 |
| SOCKS 认证 | `Checkbox` | 关闭 | 开启后显示用户/密码输入框 |
| HTTP 端口 | `OutlinedTextField`（数字） | `10809` | V2Ray 默认 HTTP 端口 |
| HTTP 认证 | `Checkbox` | 关闭 | 开启后显示用户/密码输入框 |

## 5. 输入校验

| 字段 | 规则 | 错误提示 |
|------|------|----------|
| 监听地址 | 非空，合法 IP 地址格式 | "请输入有效的 IP 地址" |
| SOCKS 端口 | 1–65535，不能与 HTTP 端口相同 | "端口范围 1–65535" / "SOCKS 和 HTTP 端口不能相同" |
| HTTP 端口 | 1–65535，不能与 SOCKS 端口相同 | 同上 |
| SOCKS 认证用户名 | 启用认证时不可为空 | "请输入用户名" |
| HTTP 认证用户名 | 启用认证时不可为空 | "请输入用户名" |
| 认证密码 | 无强制要求 | — |

校验在点击"保存"时触发，失败时对应输入框下方显示红色提示，阻止关闭。

## 6. 错误处理与边界情况

| 场景 | 处理方式 |
|------|----------|
| 首次启动无 settings.json | 使用 `GlobalSettings()` 默认值 |
| JSON 文件损坏 | 使用默认值 + 弹 Toast/提示 |
| 保存失败（磁盘满/权限） | 弹错误提示 `Dialog`，保持窗口打开 |
| 端口冲突（SOCKS = HTTP） | 校验阶段阻止，红色提示 |
| Tab 切换 | draft 保留，不丢失未保存编辑 |
| 关闭窗口（✕） | `discardDraft()`，未保存编辑丢弃 |
| 窗口打开时操作主列表 | 支持（独立 Window 非模态） |

## 7. 占位 Tab 实现

出站设置 / 路由 / DNS 三个 Tab：

```
┌─────────────────────────┐
│                         │
│        🚧               │
│      即将推出            │
│    Coming Soon          │
│                         │
└─────────────────────────┘
```

- 居中显示，灰色文字
- 不持有任何状态

## 8. 关于 Tab 内容

- 应用名称：V2RayK
- 版本号：1.0.0
- 开源协议：MIT
- GitHub 链接（如有）
- 技术栈：Kotlin + Compose Desktop

## 9. 文件清单

### 新增（8 个文件）

1. `src/main/kotlin/settings/model/GlobalSettings.kt`
2. `src/main/kotlin/settings/SettingsViewModel.kt`
3. `src/main/kotlin/settings/SettingsWindow.kt`
4. `src/main/kotlin/settings/tabs/InboundSettingsTab.kt`
5. `src/main/kotlin/settings/tabs/OutboundSettingsTab.kt`
6. `src/main/kotlin/settings/tabs/RoutingTab.kt`
7. `src/main/kotlin/settings/tabs/DnsTab.kt`
8. `src/main/kotlin/settings/tabs/AboutTab.kt`

### 修改（3 个文件）

1. `src/main/kotlin/Main.kt`
2. `src/main/kotlin/SingleColumnListWithMenu.kt`
3. `src/main/kotlin/connection/ConnectionManager.kt`

## 10. 后续扩展

- 出站设置 Tab：默认出站协议、mux 配置
- 路由 Tab：域名/IP 路由规则
- DNS Tab：DNS 服务器配置、DoH
- 全局 v2ray 可执行文件路径设置（替代当前硬编码路径）
- 设置导入/导出功能
