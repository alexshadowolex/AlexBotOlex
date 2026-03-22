package commands.twitchOnly

import config.CacheConfig
import config.TwitchBotConfig
import getVoicesFromTtsMonsterApi
import handler.Command
import isCommandDisabled
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import logger
import sendCommandDisabledMessage
import sendMessageToTwitchChatAndLogIt
import ui.SwitchStateVariables

val availableVoicesCommand = Command(
    names = listOf("availablevoices", "av", "voices"),
    description = "Show the available voices that can be used for the TTS-Command.",
    handler = { arguments ->
        if(isCommandDisabled(SwitchStateVariables.isTtsEnabled.value, messageEvent.user.name)) {
            sendCommandDisabledMessage("TTS command", chat)
            return@Command
        }

        logger.info("Getting available voices for TTS")

        var voices = CacheConfig.ttsVoices
        if(
            voices == null || voices.isEmpty() ||
            (
                arguments.isNotEmpty() &&
                arguments.first() == "refresh" &&
                messageEvent.user.name == TwitchBotConfig.channel
            )
        ) {
            voices = getVoicesFromTtsMonsterApi(chat)
        }

        if(voices != null) {
            val voicesString = voices.joinToString(", ") { it.name }
            val message = "Available voices: $voicesString"
            sendMessageToTwitchChatAndLogIt(chat, message)
        }
    }
)

@Serializable
data class TtsMonsterVoicesResponse(
    val voices: List<TtsMonsterVoice>,
    val customVoices: List<TtsMonsterVoice>
)

@Serializable
data class TtsMonsterVoice(
    @SerialName("voice_id") val voiceId: String,
    val name: String
)