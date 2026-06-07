package ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kebab.v2rayk.wrapper.config.import.ImportException
import com.kebab.v2rayk.wrapper.config.import.ImportStrategyFactory
import storage.NodeRepository

/**
 * 导入配置对话框。
 *
 * @param groups 当前已有分组名称列表
 * @param repository 节点存储仓库
 * @param onImportComplete 导入完成回调，传入 (groupName, importedNodeNames)
 * @param onDismiss 关闭对话框回调
 */
@Composable
fun ImportDialog(
    groups: List<String>,
    repository: NodeRepository,
    onImportComplete: (String, List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedGroup by remember { mutableStateOf(groups.firstOrNull() ?: "") }
    var groupDropdownExpanded by remember { mutableStateOf(false) }
    var isCreatingNewGroup by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var urlText by remember { mutableStateOf("") }
    var importErrors by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var isImporting by remember { mutableStateOf(false) }

    val effectiveGroup by remember {
        derivedStateOf {
            if (isCreatingNewGroup) newGroupName.trim() else selectedGroup
        }
    }

    val canImport by remember {
        derivedStateOf {
            urlText.isNotBlank() && effectiveGroup.isNotBlank() && !isImporting
        }
    }

    Dialog(onDismissRequest = { if (!isImporting) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colors.surface,
            elevation = 8.dp,
            modifier = Modifier.width(520.dp)
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("导入配置", style = MaterialTheme.typography.h6)
                Spacer(Modifier.height(16.dp))

                // 目标分组选择
                Text("目标分组", style = MaterialTheme.typography.subtitle2)
                Spacer(Modifier.height(4.dp))
                Box {
                    OutlinedButton(
                        onClick = { groupDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isCreatingNewGroup && newGroupName.isNotBlank()) "≡ 新建分组: $newGroupName"
                            else if (isCreatingNewGroup) "≡ 新建分组..."
                            else selectedGroup.ifBlank { "请选择分组" },
                            modifier = Modifier.weight(1f)
                        )
                        Text(" ▼")
                    }
                    DropdownMenu(
                        expanded = groupDropdownExpanded,
                        onDismissRequest = { groupDropdownExpanded = false }
                    ) {
                        groups.forEach { group ->
                            DropdownMenuItem(onClick = {
                                selectedGroup = group
                                isCreatingNewGroup = false
                                groupDropdownExpanded = false
                            }) {
                                Text(group)
                            }
                        }
                        DropdownMenuItem(onClick = {
                            isCreatingNewGroup = true
                            newGroupName = ""
                            groupDropdownExpanded = false
                        }) {
                            Text("≡ 新建分组", color = MaterialTheme.colors.primary)
                        }
                    }
                }

                // 新建分组名输入框
                if (isCreatingNewGroup) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("新分组名称") },
                        isError = isCreatingNewGroup && newGroupName.isBlank() && urlText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(16.dp))

                // 多行链接输入框
                Text("配置链接", style = MaterialTheme.typography.subtitle2)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it; importErrors = emptyMap() },
                    placeholder = { Text("每行一个分享链接，如：\nvmess://...\nss://...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 200.dp),
                    maxLines = 10,
                )

                // 错误信息
                if (importErrors.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    importErrors.forEach { (line, error) ->
                        Text(
                            "第 ${line + 1} 行: $error",
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.caption,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 底部按钮
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onDismiss,
                        enabled = !isImporting,
                    ) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            isImporting = true
                            val errors = mutableMapOf<Int, String>()
                            val importedNodes = mutableListOf<String>()

                            val lines = urlText.lines().map { it.trim() }.filter { it.isNotBlank() }
                            val targetGroup = effectiveGroup

                            lines.forEachIndexed { index, line ->
                                try {
                                    val strategy = ImportStrategyFactory.create(line)
                                    val config = strategy.parse(line)
                                    val nodeName = config.outbounds?.firstOrNull()?.tag ?: "imported-${index + 1}"
                                    repository.save(targetGroup, nodeName, config)
                                    importedNodes.add(nodeName)
                                } catch (e: ImportException) {
                                    errors[index] = e.message ?: "未知解析错误"
                                } catch (e: Exception) {
                                    errors[index] = "保存失败: ${e.message}"
                                }
                            }

                            if (importedNodes.isNotEmpty()) {
                                onImportComplete(targetGroup, importedNodes)
                            }
                            if (errors.isNotEmpty()) {
                                importErrors = errors
                            } else if (importedNodes.isNotEmpty()) {
                                onDismiss()
                            }
                            isImporting = false
                        },
                        enabled = canImport,
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text("导入")
                    }
                }
            }
        }
    }
}
