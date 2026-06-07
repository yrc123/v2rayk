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
