import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kebab.v2rayk.wrapper.V2RayCliServer
import kotlin.io.path.Path

@Composable
fun App() {
    val groups = remember { mutableStateOf(
        mapOf(
            "分组A" to listOf("条目1", "条目2"),
            "分组B" to listOf("条目3", "条目4"),
        )
    )}
    SingleColumnListWithMenu(
        groupItems = groups.value,
        onAddGroup = { groupName ->
            if (groupName.isNotBlank()) {
                groups.value = groups.value + (groupName to emptyList())
            }
        },
        onGroupSettings = { groupName ->
            // 这里可以执行具体分组设置逻辑
        }
    )
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}