import java.io.File
import java.util.*

object BotConfig {
    private val properties = Properties().apply {
        load(File("data/botconfig.properties").inputStream())
    }

    val channel: String = properties.getProperty("channel")
    val onlyMods = properties.getProperty("only_mods") == "true"
    val userCooldownSeconds = properties.getProperty("user_cooldown_seconds").toLong()
    val globalCommandCooldownSeconds = properties.getProperty("global_command_cooldown_seconds").toLong()
    val spotifyClientId: String = properties.getProperty("spotify_client_id")
    val spotifyClientSecret: String = properties.getProperty("spotify_client_secret")
    val commandPrefix: String = properties.getProperty("command_prefix")
}