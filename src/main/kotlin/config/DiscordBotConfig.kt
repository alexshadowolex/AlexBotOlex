
import dev.kord.common.entity.Snowflake
import java.io.File
import java.util.*

object DiscordBotConfig {
    private val properties = Properties().apply {
        load(File("data/discordBotconfig.properties").inputStream())
    }

    val announcementChannelId = Snowflake(properties.getProperty("announcement_channel_id").toLong())
    val announcementUsers: List<String> = properties.getProperty("announcement_users").split(",")
}