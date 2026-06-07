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
        Divider(modifier = Modifier.padding(vertical = 8.dp))
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
        Divider(modifier = Modifier.padding(vertical = 8.dp))
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
