
import dev.kord.common.Color
import dev.kord.common.entity.Snowflake
import java.io.File
import java.util.*

object DiscordBotConfig {
    private val properties = Properties().apply {
        load(File("data\\properties\\discordBotConfig.properties").inputStream())
    }

    val discordToken = File("data\\tokens\\discordToken.txt").readText()
    val announcementChannelId = Snowflake(properties.getProperty("announcement_channel_id").toLong())
    val announcementUsers: List<String> = properties.getProperty("announcement_users").split(",")
    val embedAccentColor = Color(properties.getProperty("embed_accent_color").toInt(radix = 16))
    val feedbackChannelId = Snowflake(properties.getProperty("feedback_channel_id").toLong())
    val clipChannelId = Snowflake(properties.getProperty("clip_channel_id").toLong())
}