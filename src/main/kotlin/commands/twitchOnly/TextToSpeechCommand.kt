package commands.twitchOnly

import backgroundCoroutineScope
import config.CacheConfig
import config.TwitchBotConfig
import getVoicesFromTtsMonsterApi
import handler.Command
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import isCommandDisabled
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logger
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

private data class TtsQueueEntry(
    val file: File,
    val duration: Duration
)

private val ttsQueue = mutableListOf<TtsQueueEntry>()

val textToSpeechCommand = Command(
    names = listOf("tts", "texttospeech"),
    description = "Play TTS message. If you want to use a special voice type the name in front followed by \":\", like this: \"#tts <voice>: <message>\"",
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

            var voices = CacheConfig.ttsVoices
            if(voices == null || voices.isEmpty()) {
                voices = getVoicesFromTtsMonsterApi(chat)
            }

            val httpClient = HttpClient(CIO) {
                install(Logging) {
                    logger = Logger.DEFAULT
                    level = LogLevel.NONE
                }

                install(ContentNegotiation) {
                    json()
                }
            }

            val json = Json {
                ignoreUnknownKeys = true
            }

            val defaultVoice = "Epic Narrator"

            var voiceName = defaultVoice
            var ttsText = text
            if(text.indexOf(":") >= 0) {
                val possibleVoiceName = text.substringBefore(":").trim()
                val entry = voices!!.find { it.name.lowercase() == possibleVoiceName.lowercase() }
                if(entry != null) {
                    voiceName = possibleVoiceName
                    ttsText = text
                        .replace(voiceName, "")
                        .replaceFirst(":", "")
                        .trim()
                }
            }

            val voiceId = voices!!.find { it.name.lowercase() == voiceName.lowercase() }!!.voiceId

            val endpoint = "https://api.console.tts.monster/generate"
            val httpResponse = httpClient.post(endpoint) {
                header("Authorization", TwitchBotConfig.ttsMonsterToken)
                contentType(ContentType.Application.Json)
                setBody(TtsMonsterGenerateBody(voiceId, ttsText))
            }

            if (httpResponse.status != HttpStatusCode.OK) {
                sendMessageToTwitchChatAndLogIt(chat, "Something went wrong with getting the TTS audio.")
                logger.error("Error while getting TTS audio: ${httpResponse.bodyAsText()}")
                return@Command
            }

            val response = json.decodeFromString<TtsMonsterGenerateResponse>(httpResponse.bodyAsText())
            val audioBytes = URL(response.url).readBytes()

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
                    "Playing TTS ft. $voiceName" + if (messageEvent.user.name == TwitchBotConfig.channel) {
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


@Serializable
private data class TtsMonsterGenerateBody(
    @SerialName("voice_id") val voiceId: String,
    val message: String,
    @SerialName("return_usage") val returnUsage: Boolean = false
)


@Serializable
private data class TtsMonsterGenerateResponse(
    val status: Int,
    val url: String,
    val characterUsage: Int? = null
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