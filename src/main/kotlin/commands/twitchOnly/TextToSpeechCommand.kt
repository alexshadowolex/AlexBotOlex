package commands.twitchOnly

import backgroundCoroutineScope
import config.TwitchBotConfig
import handler.Command
import httpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.jvm.javaio.*
import isCommandDisabled
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import logger
import sendCommandDisabledMessage
import sendMessageToTwitchChatAndLogIt
import ui.SwitchStateVariables
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

                addedUserCoolDown = (ttsSpeechDuration * 20).coerceAtLeast(1.minutes)
                addedCommandCoolDown = ttsSpeechDuration
                sendMessageToTwitchChatAndLogIt(
                    chat,
                    if (messageEvent.user.name == TwitchBotConfig.channel) {
                        "Playing TTS..."
                    } else {
                        "Playing TTS, putting user '${messageEvent.user.name}' on ${addedUserCoolDown.toString(DurationUnit.SECONDS, 0)} cooldown."
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
                ProcessBuilder("ffplay", "-af", "volume=1", "-nodisp", "-autoexit", "-i", entry.file.absolutePath.replace("\\","\\\\")).apply {
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