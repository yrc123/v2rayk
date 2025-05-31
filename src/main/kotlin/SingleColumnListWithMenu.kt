import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
    groupItems: Map<String, List<String>>,
    onAddGroup: (String) -> Unit,
    onGroupSettings: (String) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var doubleClickedItem by remember { mutableStateOf<String?>(null) }
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
                    title = { Text("我的菜单栏") },
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
                    backgroundColor = Color(0xFF1976D2),
                    contentColor = Color.White
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .border(2.dp, Color.Gray)
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
                        groupItems.forEach { (groupName, items) ->
                            val isFolded = groupFoldStates[groupName] ?: false
                            // 分组标题行
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFE3F2FD))
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
                            // 分组内条目（未折叠时显示）
                            if (!isFolded) {
                                items(items) { item ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                                            .background(Color(0xFFF7F7F7))
                                            .padding(12.dp)
                                            .pointerInput(item) {
                                                detectTapGestures(
                                                    onDoubleTap = {
                                                        doubleClickedItem = item
                                                    }
                                                )
                                            }
                                    ) {
                                        Text(item)
                                    }
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

                doubleClickedItem?.let { clicked ->
                    Dialog(onDismissRequest = { doubleClickedItem = null }) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
                            elevation = 8.dp,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Box(
                                Modifier.padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("双击了：$clicked")
                            }
                        }
                    }
                }
                // 添加分组对话框
                if (showAddGroupDialog) {
                    Dialog(onDismissRequest = { showAddGroupDialog = false }) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White,
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
                            color = Color.White,
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