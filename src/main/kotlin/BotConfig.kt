import java.io.File
import java.util.*

object BotConfig {
    private val properties = Properties().apply {
        load(File("data/botconfig.properties").inputStream())
    }

    val channel: String = properties.getProperty("channel")
    val onlyMods = properties.getProperty("onlymods") == "true"
    val userCooldownSeconds = properties.getProperty("usercooldownseconds").toLong()
}