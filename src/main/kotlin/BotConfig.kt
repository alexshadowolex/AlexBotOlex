import java.io.File
import java.util.*

object BotConfig {
    private val properties = Properties().apply {
        load(File("data/botconfig.properties").inputStream())
    }

    val channel: String = properties.getProperty("channel")
    val onlyMods = properties.getProperty("only_mods") == "true"
    val userCooldownSeconds = properties.getProperty("user_cooldown_seconds").toLong()
    val spotifyClientId = properties.getProperty("spotify_client_id")
    val spotifyClientSecret = properties.getProperty("spotify_client_secret")
}