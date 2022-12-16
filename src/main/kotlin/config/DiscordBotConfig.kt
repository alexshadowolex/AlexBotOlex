import dev.kord.common.Color
import dev.kord.common.entity.Snowflake
import java.io.File
import java.util.*

object DiscordBotConfig {
    private val properties = Properties().apply {
        load(File("data/discordBotconfig.properties").inputStream())
    }

    val announcementChannelId = Snowflake(properties.getProperty("announcement_channel_id").toLong())
    val embedAccentColor = Color(properties.getProperty("embed_accent_color").drop(1).toInt(radix = 16))
}