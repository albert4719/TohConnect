
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.nio.file.Paths

// 1. 定义配置文件的具体结构
data class TohConfig(
    val proxyUrl: String = "ws://ws.mcyyy.com/mc" // 这里是默认值
)

// 2. 配置文件的加载与保存管理器
object ConfigManager {
    // 配置文件存放在游戏目录的 config/toh_connect.json
    private val configFile: File = Paths.get("config", "toh_connect.json").toFile()
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    var currentConfig: TohConfig = TohConfig()
        private set

    init {
        load()
    }

    // 加载配置，如果文件不存在就创建一个默认的
    fun load() {
        currentConfig = if (configFile.exists()) {
            try {
                configFile.reader().use { gson.fromJson(it, TohConfig::class.java) } ?: TohConfig()
            } catch (e: Exception) {
                e.printStackTrace()
                TohConfig() // 读取失败则使用默认值
            }
        } else {
            save(TohConfig()) // 创建默认配置文件
            TohConfig()
        }
    }

    // 保存配置（方便以后如果你想在游戏内做修改界面）
    fun save(config: TohConfig) {
        try {
            configFile.parentFile?.mkdirs()
            configFile.writer().use { gson.toJson(config, it) }
            currentConfig = config
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}