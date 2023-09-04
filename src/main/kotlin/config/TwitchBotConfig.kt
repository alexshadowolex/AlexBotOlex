package config

import java.io.File
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object TwitchBotConfig {
    private val properties = Properties().apply {
        load(File("data\\properties\\twitchBotConfig.properties").inputStream())
    }

    val chatAccountToken = File("data\\tokens\\twitchToken.txt").readText()
    val channel: String = properties.getProperty("channel")
    val commandPrefix: String = properties.getProperty("command_prefix")
    val songRequestEmotes: List<String> = properties.getProperty("song_request_emotes").split(",")
    val soundAlertDirectory: String = properties.getProperty("sound_alert_directory")
    val allowedSoundFiles: List<String> = properties.getProperty("allowed_sound_files").split(",")
    val levenshteinThreshold = properties.getProperty("levenshtein_threshold").toInt()
    val defaultUserCoolDown = properties.getProperty("default_user_cool_down").toInt().seconds
    val defaultCommandCoolDown = properties.getProperty("default_command_cool_down").toInt().seconds
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
    val isMemeQueueEnabledByDefault: Boolean = properties.getProperty("is_meme_queue_enabled_by_default").toBoolean()
    val isSongCommandEnabledByDefault: Boolean = properties.getProperty("is_song_command_enabled_by_default").toBoolean()
    val isSongLouderEnabledByDefault: Boolean = properties.getProperty("is_song_louder_enabled_by_default").toBoolean()
    val isSpotifyQueueEnabledByDefault: Boolean = properties.getProperty("is_spotify_queue_enabled_by_default").toBoolean()
    val isVoteSkipEnabledByDefault: Boolean = properties.getProperty("is_vote_skip_enabled_by_default").toBoolean()
    val isFeedbackEnabledByDefault: Boolean = properties.getProperty("is_feedback_enabled_by_default").toBoolean()
    val isSendClipEnabledByDefault: Boolean = properties.getProperty("is_send_clip_enabled_by_default").toBoolean()
    val commandDisabledEmote1: String = properties.getProperty("command_disabled_emote1")
    val commandDisabledEmote2: String = properties.getProperty("command_disabled_emote2")
    val thisEmote: String = properties.getProperty("this_emote")
    val worryStickEmote: String = properties.getProperty("worry_stick_emote")
    val raidMessageText: String = properties.getProperty("raid_message_text")
    val raidMessageAmountRepetitions = properties.getProperty("raid_message_amount_repetitions").toInt()
    val shrugEmote: String = properties.getProperty("shrug_emote")
    val peepoDjEmote: String = properties.getProperty("peepo_dj_emote")
    val pepeVibeHardEmote: String = properties.getProperty("pepe_vibe_hard_emote")
    val yikersEmote: String = properties.getProperty("yikers_emote")
}