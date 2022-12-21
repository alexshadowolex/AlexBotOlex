package config

import java.io.File
import java.util.*
import kotlin.time.Duration.Companion.seconds

object TwitchBotConfig {
    private val properties = Properties().apply {
        load(File("data/twitchBotconfig.properties").inputStream())
    }

    val channel: String = properties.getProperty("channel")
    val onlyMods = properties.getProperty("only_mods") == "true"
    val spotifyClientId: String = properties.getProperty("spotify_client_id")
    val spotifyClientSecret: String = properties.getProperty("spotify_client_secret")
    val commandPrefix: String = properties.getProperty("command_prefix")
    val songRequestEmotes: List<String> = properties.getProperty("song_request_emotes").split(",")
    val soundAlertDirectory: String = properties.getProperty("sound_alert_directory")
    val allowedSoundFiles: List<String> = properties.getProperty("allowed_sound_files").split(",")
    val levenshteinThreshold = properties.getProperty("levenshtein_threshold").toInt()
    val userCooldown = properties.getProperty("user_cooldown").toInt().seconds
    val commandCooldown = properties.getProperty("command_cooldown").toInt().seconds
    val rejectEmote: String = properties.getProperty("reject_emote")
    val confirmEmote: String = properties.getProperty("confirm_emote")
    val explanationEmote: String = properties.getProperty("explanation_emote")
    val allowedDomains: List<String> = properties.getProperty("allowed_domains").split(",")
}