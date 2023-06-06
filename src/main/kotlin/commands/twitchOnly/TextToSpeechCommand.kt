package commands.twitchOnly

import handler.Command
import config.TwitchBotConfig
import httpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.jvm.javaio.*
import ui.isTtsEnabled
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import logger
import java.io.File
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

@OptIn(InternalAPI::class)
val textToSpeechCommand = Command(
    names = listOf("tts", "texttospeech"),
    description = "Play TTS message. The given message has to be written behind the command.",
    handler = { arguments ->
        if(!isTtsEnabled && TwitchBotConfig.channel != messageEvent.user.name) {
            logger.info("TTS is disabled, aborting command execution.")
            chat.sendMessage(TwitchBotConfig.channel, "TTS is disabled ${TwitchBotConfig.commandDisabledEmote1} Now suck my ${TwitchBotConfig.commandDisabledEmote2}")
            return@Command
        }

        if (arguments.isEmpty()) {
            chat.sendMessage(TwitchBotConfig.channel, "No input provided.")
            logger.info("No TTS input provided.")
            return@Command
        }

        val text = arguments.joinToString(" ")

        try {
            logger.info("Playing TTS from message '$text'...")

            val url = httpClient.post("https://streamlabs.com/polly/speak") {
                contentType(ContentType.Application.Json)

                setBody(TtsRequest(voice = "Brian", text = text))
            }.body<TtsResponse>().speakUrl

            logger.info("Streamlabs returned URL '$url'.")

            withContext(Dispatchers.IO) {
                val ttsDataFile = File.createTempFile("tts_", ".mp3").apply {
                    writeBytes(httpClient.get(url).body<HttpResponse>().content.toInputStream().readAllBytes())
                }

                val ttsSpeechDuration = ProcessBuilder("ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=noprint_wrappers=1:nokey=1", ttsDataFile.absolutePath.replace("\\","\\\\")).start().run {
                    while (isAlive) {
                        delay(100.milliseconds)
                    }

                    inputReader().readText().trim().toDouble().seconds
                }

                addedUserCooldown = (ttsSpeechDuration * 20).coerceAtLeast(1.minutes)
                addedCommandCooldown = ttsSpeechDuration
                chat.sendMessage(
                    TwitchBotConfig.channel,
                    if (messageEvent.user.name == TwitchBotConfig.channel) {
                        "Playing TTS..."
                    } else {
                        "Playing TTS, putting user '${messageEvent.user.name}' on ${addedUserCooldown.toString(DurationUnit.SECONDS, 0)} cooldown."
                    }
                )

                ttsQueue.add(TtsQueueEntry(ttsDataFile, ttsSpeechDuration))
            }
        } catch (e: Exception) {
            chat.sendMessage(TwitchBotConfig.channel, "Unable to play TTS.")
            logger.error("Unable to play TTS:", e)
        }
    }
)

private val ttsPlayerCoroutineScope = CoroutineScope(Dispatchers.IO)

@Suppress("unused")
val ttsPlayerJob = ttsPlayerCoroutineScope.launch {
    while (isActive) {
        ttsQueue.removeFirstOrNull()?.let { entry ->
            ProcessBuilder("ffplay", "-af", "volume=2", "-nodisp", "-autoexit", "-i", entry.file.absolutePath.replace("\\","\\\\")).apply {
                inheritIO()
            }.start().onExit().await()

            delay(3.seconds)
        } ?: run {
            delay(1.seconds)
        }
    }
}