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
                        }
                        // 保存失败留在窗口
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
