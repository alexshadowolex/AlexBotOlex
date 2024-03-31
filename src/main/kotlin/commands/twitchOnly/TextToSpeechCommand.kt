package commands.twitchOnly

import backgroundCoroutineScope
import config.TwitchBotConfig
import handler.Command
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import isCommandDisabled
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import logger
import okio.internal.commonToUtf8String
import sendCommandDisabledMessage
import sendMessageToTwitchChatAndLogIt
import ui.SwitchStateVariables
import java.io.File
import java.net.URL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

@Serializable
private data class TtsRequest(
    val voice: String,
    val text: String
)

@Serializable
private data class TtsResponse(
    val success: Boolean,
    @SerialName("speak_url") val speakUrl: String
)

private data class TtsQueueEntry(
    val file: File,
    val duration: Duration
)

private val ttsQueue = mutableListOf<TtsQueueEntry>()

val textToSpeechCommand = Command(
    names = listOf("tts", "texttospeech"),
    description = "Play TTS message. The given message has to be written behind the command.",
    handler = { arguments ->
        if(isCommandDisabled(SwitchStateVariables.isTtsEnabled.value, messageEvent.user.name)) {
            sendCommandDisabledMessage("TTS command", chat)
            return@Command
        }

        if (arguments.isEmpty()) {
            sendMessageToTwitchChatAndLogIt(chat, "No input provided.")
            return@Command
        }

        val text = arguments.joinToString(" ")

        try {
            logger.info("Trying to play TTS from message '$text'...")

            val httpClient = HttpClient(CIO) {
                install(Logging) {
                    logger = Logger.DEFAULT
                    level = LogLevel.NONE
                }
            }

            val endpoint = "http://api.voicerss.org/"
            val errorTextMessageStart = "ERROR:"
            val currentVariation = listOf(
                "en-au" to listOf("Zoe", "Isla", "Evie", "Jack"),
                "en-ca" to listOf("Rose", "Clara", "Emma", "Mason"),
                "en-gb" to listOf("Alice", "Nancy", "Lily", "Harry"),
                "en-in" to listOf("Eka", "Jai", "Ajit"),
                "en-ie" to listOf("Oran"),
                "en-us" to listOf("Linda", "Amy", "Mary", "John", "Mike")
            ).random()

            val language = currentVariation.first
            val voice = currentVariation.second.random()
            logger.info("Chose random language \"$language\" with voice \"$voice\"")

            val response = httpClient.get(endpoint) {
                parameter("key", TwitchBotConfig.voiceRssToken)
                parameter("src", text)
                parameter("hl", language)
                parameter("v", voice)
            }

            // Hard coded cuz the API is a joke
            val audioUrl = response
                .toString().substringAfter("[").substringBeforeLast("]")
                .split(",")[0]

            val audioBytes = URL(audioUrl).readBytes()
            if(audioBytes.commonToUtf8String().startsWith(errorTextMessageStart)) {
                sendMessageToTwitchChatAndLogIt(chat, "Something went wrong with getting the TTS audio.")
                logger.error("Error while getting TTS audio: ${audioBytes.commonToUtf8String()}")
                return@Command
            }

            withContext(Dispatchers.IO) {
                val ttsDataFile = File.createTempFile("tts_", ".wav").apply {
                    writeBytes(audioBytes)
                }

                val ttsSpeechDuration = ProcessBuilder(
                    "ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1",
                    ttsDataFile.absolutePath.replace("\\","\\\\")
                ).start().run {
                    while (isAlive) {
                        delay(100.milliseconds)
                    }

                    inputReader().readText().trim().toDouble().seconds
                }

                addedUserCoolDown = (ttsSpeechDuration * 20).coerceAtLeast(1.minutes)
                addedCommandCoolDown = ttsSpeechDuration
                sendMessageToTwitchChatAndLogIt(
                    chat,
                    "Playing TTS ft. $voice" + if (messageEvent.user.name == TwitchBotConfig.channel) {
                        ""
                    } else {
                        ", putting user " +
                        "${messageEvent.user.name} " +
                        "on ${addedUserCoolDown.toString(DurationUnit.SECONDS, 0)} cooldown."
                    }
                )

                ttsQueue.add(TtsQueueEntry(ttsDataFile, ttsSpeechDuration))
            }
        } catch (e: Exception) {
            sendMessageToTwitchChatAndLogIt(chat, "Unable to play TTS.")
            logger.error("Unable to play TTS:", e)
        }
    }
)

@Suppress("unused")
val ttsPlayerJob = backgroundCoroutineScope.launch {
    while (isActive) {
        ttsQueue.removeFirstOrNull()?.let { entry ->
            val ttsProcess = withContext(Dispatchers.IO) {
                ProcessBuilder(
                    "ffplay", "-af", "volume=2", "-nodisp", "-autoexit", "-i",
                    entry.file.absolutePath.replace("\\","\\\\")).apply {
                        inheritIO()
                    }.start().onExit().await()
            }

            while (ttsProcess!!.isAlive) {
                supervisorScope {
                    try {
                        delay(0.1.seconds)
                    } catch (_: Exception) {
                        ttsProcess.destroyForcibly()
                    }
                }
            }

            delay(3.seconds)
        } ?: run {
            delay(1.seconds)
        }
    }
}