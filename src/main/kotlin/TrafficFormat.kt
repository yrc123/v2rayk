import java.util.Locale

/**
 * 按 1024 进制把字节数格式化为 B/KB/MB/GB/TB。
 * - 小于 1024 字节：整数 + "B"，不带小数（如 "512 B"）。
 * - 其余：保留 1 位小数（如 "1.5 KB"、"1.0 MB"）。
 * 使用 Locale.ROOT 保证不同区域设置下输出一致（跨平台确定性），纯函数、无 OS 假设。
 */
fun formatTraffic(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.ROOT, "%.1f %s", value, units[unitIndex])
}
