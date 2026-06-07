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
