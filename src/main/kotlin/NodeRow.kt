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
