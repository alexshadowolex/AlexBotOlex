package config

import java.io.File
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
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
    val defaultUserCooldown = properties.getProperty("default_user_cooldown").toInt().seconds
    val defaultCommandCooldown = properties.getProperty("default_command_cooldown").toInt().seconds
    val rejectEmote: String = properties.getProperty("reject_emote")
    val confirmEmote: String = properties.getProperty("confirm_emote")
    val explanationEmote: String = properties.getProperty("explanation_emote")
    val allowedDomains: List<String> = properties.getProperty("allowed_domains").split(",")
    val timerDurationMinutes: Duration = properties.getProperty("timer_duration_minutes").toInt().minutes
    val blacklistedUsers: List<String> = properties.getProperty("black_list_users").split(",")
    val blacklistEmote: String = properties.getProperty("blacklist_emote")
    val isSongRequestEnabledByDefault: Boolean = properties.getProperty("is_song_request_enabled_by_default").toBoolean()
    val isSoundAlertEnabledByDefault: Boolean = properties.getProperty("is_sound_alert_enabled_by_default").toBoolean()
    val isTtsEnabledByDefault: Boolean = properties.getProperty("is_tts_enabled_by_default").toBoolean()
    val commandDisabledEmote1: String = properties.getProperty("command_disabled_emote1")
    val commandDisabledEmote2: String = properties.getProperty("command_disabled_emote2")
    val thisEmote: String = properties.getProperty("this_emote")
}