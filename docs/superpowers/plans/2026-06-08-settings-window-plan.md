# 设置窗口（独立 Window + Tab 架构）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 TopAppBar "选项"下拉菜单改为独立设置窗口，Tab 架构组织配置，首期实现入站设置和关于 Tab。

**Architecture:** 新增 `settings/` 包，`SettingsViewModel` 持有全局设置状态（settings + draft 双 StateFlow），`SettingsWindow` 在独立 Compose `Window` 中用 `ScrollableTabRow` 切换各 Tab。`ConnectionManager.switchTo()` 接收 `GlobalSettings` 动态生成 SOCKS/HTTP 入站配置。

**Tech Stack:** Kotlin 2.1.0 + Compose Desktop 1.7.3 + Jackson (JSON 持久化) + kotlinx.coroutines

---

## 文件结构

### 新增（8 个文件）

| 文件 | 职责 |
|------|------|
| `settings/model/GlobalSettings.kt` | 全局设置 data class + 入站配置 data class |
| `settings/SettingsViewModel.kt` | 设置状态持有者：load/save/draft 管理 |
| `settings/SettingsWindow.kt` | 独立 Window 宿主：TabRow + 内容区 + 底部按钮栏 |
| `settings/tabs/InboundSettingsTab.kt` | 入站设置表单 Composable |
| `settings/tabs/OutboundSettingsTab.kt` | 占位："即将推出" |
| `settings/tabs/RoutingTab.kt` | 占位："即将推出" |
| `settings/tabs/DnsTab.kt` | 占位："即将推出" |
| `settings/tabs/AboutTab.kt` | 关于信息 |

### 修改（3 个文件）

| 文件 | 变更 |
|------|------|
| `Main.kt` | "选项"→"设置"按钮；创建 ViewModel；条件渲染设置 Window；switchTo 传入 globalSettings |
| `SingleColumnListWithMenu.kt` | 移除"添加分组"/"关于"菜单项；分组列表底部新增"+ 添加分组"按钮 |
| `ConnectionManager.kt` | `switchTo()` 新增 `GlobalSettings` 参数，生成 SOCKS + HTTP 入站 |

---

### Task 1: 创建 GlobalSettings 数据模型

**Files:**
- Create: `src/main/kotlin/settings/model/GlobalSettings.kt`

- [ ] **Step 1: 创建 settings/model 目录并写入 GlobalSettings.kt**

```kotlin
package settings.model

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
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

- [ ] **Step 2: 编译验证**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add src/main/kotlin/settings/model/GlobalSettings.kt
git commit -m "feat: add GlobalSettings and InboundGlobalConfig data models

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: 创建 SettingsViewModel

**Files:**
- Create: `src/main/kotlin/settings/SettingsViewModel.kt`

- [ ] **Step 1: 写入 SettingsViewModel.kt**

```kotlin
package settings

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import settings.model.GlobalSettings
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class SettingsViewModel(
    private val configPath: Path = Paths.get(
        System.getProperty("user.home"), ".v2rayk", "settings.json"
    ),
) {
    private val objectMapper = ObjectMapper().registerKotlinModule()

    private val _settings = MutableStateFlow(GlobalSettings())
    val settings: StateFlow<GlobalSettings> = _settings.asStateFlow()

    private val _draft = MutableStateFlow(GlobalSettings())
    val draft: StateFlow<GlobalSettings> = _draft.asStateFlow()

    /** 加载成功/失败的标记，供 UI 展示警告 */
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    init {
        load()
    }

    /** 从 JSON 文件加载设置到 _settings，同时初始化 _draft */
    fun load() {
        if (Files.exists(configPath)) {
            try {
                val loaded: GlobalSettings = objectMapper.readValue(configPath.toFile())
                _settings.value = loaded
                _draft.value = loaded
                _loadError.value = null
            } catch (e: Exception) {
                // JSON 损坏，使用默认值
                _settings.value = GlobalSettings()
                _draft.value = GlobalSettings()
                _loadError.value = "设置文件损坏，已恢复为默认值"
            }
        } else {
            // 首次启动，使用默认值
            _settings.value = GlobalSettings()
            _draft.value = GlobalSettings()
            _loadError.value = null
        }
    }

    /** 持久化 draft 到 JSON 文件并提交到 settings */
    fun save(): Result<Unit> {
        return try {
            Files.createDirectories(configPath.parent)
            val json = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(_draft.value)
            configPath.toFile().writeText(json)
            _settings.value = _draft.value
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 修改 draft */
    fun updateDraft(transform: (GlobalSettings) -> GlobalSettings) {
        _draft.update { transform(it) }
    }

    /** 放弃草稿，重置为当前 settings */
    fun discardDraft() {
        _draft.value = _settings.value
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add src/main/kotlin/settings/SettingsViewModel.kt
git commit -m "feat: add SettingsViewModel with settings/draft dual-state and JSON persistence

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: 创建占位 Tab（3 个文件）

**Files:**
- Create: `src/main/kotlin/settings/tabs/OutboundSettingsTab.kt`
- Create: `src/main/kotlin/settings/tabs/RoutingTab.kt`
- Create: `src/main/kotlin/settings/tabs/DnsTab.kt`

- [ ] **Step 1: 创建 tabs 目录并写入 OutboundSettingsTab.kt**

先创建目录：
```bash
mkdir -p src/main/kotlin/settings/tabs
```

```kotlin
package settings.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun OutboundSettingsTab() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "即将推出\nComing Soon",
            style = MaterialTheme.typography.subtitle1,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.38f),
        )
    }
}
```

- [ ] **Step 2: 写入 RoutingTab.kt**

```kotlin
package settings.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun RoutingTab() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "即将推出\nComing Soon",
            style = MaterialTheme.typography.subtitle1,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.38f),
        )
    }
}
```

- [ ] **Step 3: 写入 DnsTab.kt**

```kotlin
package settings.tabs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun DnsTab() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "即将推出\nComing Soon",
            style = MaterialTheme.typography.subtitle1,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.38f),
        )
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add src/main/kotlin/settings/tabs/OutboundSettingsTab.kt src/main/kotlin/settings/tabs/RoutingTab.kt src/main/kotlin/settings/tabs/DnsTab.kt
git commit -m "feat: add placeholder tabs for outbound, routing, and DNS settings

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: 创建 AboutTab

**Files:**
- Create: `src/main/kotlin/settings/tabs/AboutTab.kt`

- [ ] **Step 1: 写入 AboutTab.kt**

```kotlin
package settings.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AboutTab() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "V2RayK",
                style = MaterialTheme.typography.h4,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "版本 1.0.0",
                style = MaterialTheme.typography.body1,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "V2Ray 桌面 GUI 客户端",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "基于 Kotlin + Compose Desktop 构建",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.38f),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "开源协议：MIT",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.38f),
            )
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add src/main/kotlin/settings/tabs/AboutTab.kt
git commit -m "feat: add About tab with app version and license info

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: 创建 InboundSettingsTab 表单

**Files:**
- Create: `src/main/kotlin/settings/tabs/InboundSettingsTab.kt`

- [ ] **Step 1: 写入 InboundSettingsTab.kt**

```kotlin
package settings.tabs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import settings.model.HttpAuthConfig
import settings.model.InboundGlobalConfig
import settings.model.SocksAuthConfig

/**
 * 入站设置表单。
 *
 * @param config 当前草稿中的入站配置
 * @param onConfigChange 用户修改字段时回调，传入变换后的新 InboundGlobalConfig
 */
@Composable
fun InboundSettingsTab(
    config: InboundGlobalConfig,
    onConfigChange: (InboundGlobalConfig) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 监听地址
        OutlinedTextField(
            value = config.listen,
            onValueChange = { onConfigChange(config.copy(listen = it)) },
            label = { Text("监听地址") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // --- SOCKS ---
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("SOCKS 入站", style = MaterialTheme.typography.subtitle1)

        OutlinedTextField(
            value = config.socksPort.toString(),
            onValueChange = { raw ->
                raw.toIntOrNull()?.let { port ->
                    onConfigChange(config.copy(socksPort = port))
                }
            },
            label = { Text("SOCKS 端口") },
            singleLine = true,
            modifier = Modifier.width(200.dp),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = config.socksUdpEnabled,
                onCheckedChange = { onConfigChange(config.copy(socksUdpEnabled = it)) },
            )
            Text("启用 UDP")
        }

        var socksAuthEnabled by remember(config) {
            mutableStateOf(config.socksAuth != null)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = socksAuthEnabled,
                onCheckedChange = { enabled ->
                    socksAuthEnabled = enabled
                    onConfigChange(
                        config.copy(
                            socksAuth = if (enabled) SocksAuthConfig() else null
                        )
                    )
                },
            )
            Text("SOCKS 认证")
        }

        AnimatedVisibility(visible = socksAuthEnabled) {
            Column(
                modifier = Modifier.padding(start = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val auth = config.socksAuth ?: SocksAuthConfig()
                OutlinedTextField(
                    value = auth.user,
                    onValueChange = { onConfigChange(config.copy(socksAuth = auth.copy(user = it))) },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.width(240.dp),
                )
                OutlinedTextField(
                    value = auth.pass,
                    onValueChange = { onConfigChange(config.copy(socksAuth = auth.copy(pass = it))) },
                    label = { Text("密码") },
                    singleLine = true,
                    modifier = Modifier.width(240.dp),
                )
            }
        }

        // --- HTTP ---
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("HTTP 入站", style = MaterialTheme.typography.subtitle1)

        OutlinedTextField(
            value = config.httpPort.toString(),
            onValueChange = { raw ->
                raw.toIntOrNull()?.let { port ->
                    onConfigChange(config.copy(httpPort = port))
                }
            },
            label = { Text("HTTP 端口") },
            singleLine = true,
            modifier = Modifier.width(200.dp),
        )

        var httpAuthEnabled by remember(config) {
            mutableStateOf(config.httpAuth != null)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = httpAuthEnabled,
                onCheckedChange = { enabled ->
                    httpAuthEnabled = enabled
                    onConfigChange(
                        config.copy(
                            httpAuth = if (enabled) HttpAuthConfig() else null
                        )
                    )
                },
            )
            Text("HTTP 认证")
        }

        AnimatedVisibility(visible = httpAuthEnabled) {
            Column(
                modifier = Modifier.padding(start = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val auth = config.httpAuth ?: HttpAuthConfig()
                OutlinedTextField(
                    value = auth.user,
                    onValueChange = { onConfigChange(config.copy(httpAuth = auth.copy(user = it))) },
                    label = { Text("用户名") },
                    singleLine = true,
                    modifier = Modifier.width(240.dp),
                )
                OutlinedTextField(
                    value = auth.pass,
                    onValueChange = { onConfigChange(config.copy(httpAuth = auth.copy(pass = it))) },
                    label = { Text("密码") },
                    singleLine = true,
                    modifier = Modifier.width(240.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add src/main/kotlin/settings/tabs/InboundSettingsTab.kt
git commit -m "feat: add InboundSettingsTab form with listen address, SOCKS/HTTP ports, UDP, auth

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: 创建 SettingsWindow 宿主

**Files:**
- Create: `src/main/kotlin/settings/SettingsWindow.kt`

- [ ] **Step 1: 写入 SettingsWindow.kt**

```kotlin
package settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import settings.model.InboundGlobalConfig
import settings.tabs.*

/**
 * 设置窗口的 Tab 枚举。
 */
enum class SettingsTab(val label: String) {
    INBOUND("入站设置"),
    OUTBOUND("出站设置"),
    ROUTING("路由"),
    DNS("DNS"),
    ABOUT("关于"),
}

/**
 * 设置窗口 Composable，嵌入独立 Compose [Window]。
 *
 * @param viewModel 全局设置 ViewModel
 * @param onSave 保存并关闭回调
 * @param onCancel 取消并关闭回调
 */
@Composable
fun SettingsWindow(
    viewModel: SettingsViewModel,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val draft by viewModel.draft.collectAsState()

    var selectedTab by remember { mutableStateOf(SettingsTab.INBOUND) }
    // 校验错误状态
    var validationErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    MaterialTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            // Tab 栏
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 16.dp,
            ) {
                SettingsTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) },
                        enabled = tab == SettingsTab.INBOUND
                            || tab == SettingsTab.OUTBOUND
                            || tab == SettingsTab.ROUTING
                            || tab == SettingsTab.DNS
                            || tab == SettingsTab.ABOUT,
                    )
                }
            }

            // 内容区
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    SettingsTab.INBOUND -> InboundSettingsTab(
                        config = draft.inbound,
                        onConfigChange = { newInbound ->
                            viewModel.updateDraft { it.copy(inbound = newInbound) }
                        },
                    )
                    SettingsTab.OUTBOUND -> OutboundSettingsTab()
                    SettingsTab.ROUTING -> RoutingTab()
                    SettingsTab.DNS -> DnsTab()
                    SettingsTab.ABOUT -> AboutTab()
                }
            }

            // 校验错误提示
            if (validationErrors.isNotEmpty()) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    validationErrors.values.forEach { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.caption,
                        )
                    }
                }
            }

            // 底部按钮栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) {
                    Text("取消")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val errors = validate(draft.inbound)
                    validationErrors = errors
                    if (errors.isEmpty()) {
                        val result = viewModel.save()
                        if (result.isSuccess) {
                            onSave()
                        } else {
                            // 保存失败留在窗口，错误已在 ViewModel 处理
                        }
                    }
                }) {
                    Text("保存")
                }
            }
        }
    }
}

/**
 * 校验入站配置，返回 fieldName -> errorMessage 的 map。
 */
private fun validate(config: InboundGlobalConfig): Map<String, String> {
    val errors = mutableMapOf<String, String>()

    // 监听地址校验
    val ipRegex = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")
    if (config.listen.isBlank()) {
        errors["listen"] = "请输入监听地址"
    } else if (!ipRegex.matches(config.listen)) {
        errors["listen"] = "请输入有效的 IP 地址"
    }

    // 端口范围校验
    if (config.socksPort !in 1..65535) {
        errors["socksPort"] = "端口范围 1–65535"
    }
    if (config.httpPort !in 1..65535) {
        errors["httpPort"] = "端口范围 1–65535"
    }

    // 端口互斥
    if (config.socksPort == config.httpPort) {
        errors["ports"] = "SOCKS 和 HTTP 端口不能相同"
    }

    // 认证用户名校验
    if (config.socksAuth != null && config.socksAuth.user.isBlank()) {
        errors["socksAuthUser"] = "请输入 SOCKS 认证用户名"
    }
    if (config.httpAuth != null && config.httpAuth.user.isBlank()) {
        errors["httpAuthUser"] = "请输入 HTTP 认证用户名"
    }

    return errors
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add src/main/kotlin/settings/SettingsWindow.kt
git commit -m "feat: add SettingsWindow with ScrollableTabRow, form validation, and save/cancel

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 7: 修改 SingleColumnListWithMenu — 更新导航栏与添加分组

**Files:**
- Modify: `src/main/kotlin/SingleColumnListWithMenu.kt`

- [ ] **Step 1: 阅读当前文件确认基线**

```bash
cat src/main/kotlin/SingleColumnListWithMenu.kt
```

- [ ] **Step 2: 修改 TopAppBar — "选项"下拉改为"设置"按钮，移除"关于"**

找到 `TopAppBar` 的 `actions` 部分，替换 DropdownMenu 为单一 IconButton：

```kotlin
// 替换 actions 块中的 Box { ... DropdownMenu ... } 为：
IconButton(onClick = onSettings) {  // onSettings 需在参数中新增
    Text("设置")
}
```

即 actions 变为：
```kotlin
actions = {
    IconButton(onClick = onImportClick) {
        Text("导入")
    }
    IconButton(onClick = onSettings) {
        Text("设置")
    }
},
```

- [ ] **Step 3: 移除 menuExpanded 和相关 DropdownMenu 状态**

删除以下不再需要的状态声明：
- `var menuExpanded by remember { mutableStateOf(false) }`
- `var showAddGroupDialog by remember { mutableStateOf(false) }`
- `var newGroupName by remember { mutableStateOf("") }`

- [ ] **Step 4: 保留"添加分组"对话框，但改为由列表底部按钮触发**

在 LazyColumn 末尾新增一个 item 作为"+ 添加分组"按钮：

```kotlin
// 在 groupItems.forEach { ... } 之后、LazyColumn 闭括号之前添加：
item {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        TextButton(onClick = { showAddGroupDialog = true }) {
            Text("+ 添加分组")
        }
    }
}
```

并将 `showAddGroupDialog` 和 `newGroupName` 的声明保留（因为对话框仍需要它们）。

- [ ] **Step 5: 新增 onSettings 参数**

在 `SingleColumnListWithMenu` 函数签名中新增参数：

```kotlin
@Composable
fun SingleColumnListWithMenu(
    groupItems: Map<String, List<ProxyNode>>,
    selectedNode: ProxyNode?,
    connectedNode: ProxyNode?,
    onSelectNode: (ProxyNode) -> Unit,
    onConnectNode: (ProxyNode) -> Unit,
    onDisconnectNode: (ProxyNode) -> Unit,
    onAddGroup: (String) -> Unit,
    onGroupSettings: (String) -> Unit,
    onImportClick: () -> Unit,
    onSettings: () -> Unit,  // 新增
) {
```

- [ ] **Step 6: 编译验证**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL（如果 Main.kt 还没有传 onSettings 会报错，预期行为，下一步修复）

- [ ] **Step 7: 提交**

```bash
git add src/main/kotlin/SingleColumnListWithMenu.kt
git commit -m "feat: replace Options dropdown with Settings button, move Add Group to list bottom

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 8: 修改 ConnectionManager — 集成全局入站设置

**Files:**
- Modify: `src/main/kotlin/ConnectionManager.kt`

- [ ] **Step 1: 修改 switchTo 签名并实现入站生成逻辑**

```kotlin
import settings.model.GlobalSettings
import com.kebab.v2rayk.wrapper.config.Inbound
import com.kebab.v2rayk.wrapper.config.bound.ProtocolType
import com.kebab.v2rayk.wrapper.config.bound.settings.SocksInboundSettings
import com.kebab.v2rayk.wrapper.config.bound.settings.HttpInboundSettings
import com.kebab.v2rayk.wrapper.config.bound.settings.SocksAccount
import com.kebab.v2rayk.wrapper.config.bound.settings.HttpAccount
import com.kebab.v2rayk.wrapper.config.V2rayProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
```

替换 `switchTo` 方法：

```kotlin
private val configMapper = ObjectMapper().registerKotlinModule()

/**
 * 切换到指定节点，合并全局入站设置后启动 V2Ray。
 */
fun switchTo(node: ProxyNode, globalSettings: GlobalSettings): Result<Unit> {
    disconnect()
    return connect(node, globalSettings)
}

/**
 * 连接到指定节点，合并全局入站设置到节点配置。
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
            auth = if (inbound.socksAuth != null)
                com.kebab.v2rayk.wrapper.config.bound.settings.SocksAuth.PASSWORD
            else
                com.kebab.v2rayk.wrapper.config.bound.settings.SocksAuth.NOAUTH,
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
```

注意：原 `connect(node: ProxyNode): Result<Unit>` 和 `switchTo(node: ProxyNode): Result<Unit>` 方法需移除，替换为上述新签名版本。

- [ ] **Step 2: 编译验证**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add src/main/kotlin/ConnectionManager.kt
git commit -m "feat: integrate GlobalSettings into ConnectionManager for dynamic inbound config

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 9: 修改 Main.kt — 集成设置窗口与 ViewModel

**Files:**
- Modify: `src/main/kotlin/Main.kt`

- [ ] **Step 1: 阅读当前 Main.kt 确认基线**

```bash
cat src/main/kotlin/Main.kt
```

- [ ] **Step 2: 在 App() 中新增 state 和 ViewModel**

```kotlin
// 在 App() 中，connectionManager 声明之后添加：
val settingsViewModel = remember { SettingsViewModel() }
var showSettings by remember { mutableStateOf(false) }
```

- [ ] **Step 3: 修改 SingleColumnListWithMenu 调用，新增 onSettings 参数**

```kotlin
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
    onSettings = {  // 新增
        showSettings = true
    },
)
```

- [ ] **Step 4: 添加独立设置 Window 渲染**

在 `App()` 末尾（`if (errorMessage != null)` 之前或之后）添加：

```kotlin
// 设置窗口（独立 Window）
if (showSettings) {
    androidx.compose.ui.window.Window(
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
```

- [ ] **Step 5: 清理未使用的 import 并确保编译**

检查 Main.kt 中是否还有引旧 `switchTo(node)` 签名的调用，全部改为 `switchTo(node, settingsViewModel.settings.value)`。

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 提交**

```bash
git add src/main/kotlin/Main.kt
git commit -m "feat: integrate SettingsWindow and SettingsViewModel into Main.kt

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 10: 构建验证与端到端测试

**Files:**
- Verify: 整个项目编译

- [ ] **Step 1: 完整项目构建**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 手动验证清单**

启动应用：
```bash
./gradlew run
```

验证以下行为：
1. TopAppBar 显示"导入"和"设置"两个按钮（无"选项"下拉）
2. 点击"设置"→ 弹出独立窗口，标题"设置"
3. 5 个 Tab：入站设置（可用）、出站设置/路由/DNS（灰色占位）、关于（可用）
4. 入站设置 Tab 显示默认值：监听 0.0.0.0、SOCKS 10808、HTTP 10809
5. 勾选 SOCKS 认证 → 显示用户名/密码输入框；取消勾选 → 隐藏
6. 清空监听地址点保存 → 红色提示"请输入监听地址"
7. SOCKS 和 HTTP 填相同端口点保存 → 红色提示
8. 点取消关闭窗口，重新打开 → 值恢复为保存前的状态
9. 修改并保存 → 关闭窗口重新打开 → 显示保存后的值
10. 分组列表底部有"+ 添加分组"按钮，可正常添加
11. 关于 Tab 显示 V2RayK 版本信息

- [ ] **Step 3: 提交最终验证通过的代码**

```bash
git add -A
git commit -m "chore: finalize settings window integration with build verification

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```
