package commands

import BotConfig
import Command
import com.adamratzman.spotify.utils.Language
import httpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import javazoom.jl.player.Player
import kotlinx.coroutines.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import logger
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.mp3.MP3AudioHeader
import java.io.File
import javax.sound.sampled.AudioSystem
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

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

private val ttsFileQueue = mutableListOf<TtsQueueEntry>()

val textToSpeechCommand = Command(
    names = listOf("tts", "texttospeech"),
    handler = { arguments ->
        if (arguments.isEmpty()) {
            chat.sendMessage(BotConfig.channel, "No input provided.")
            logger.info("No TTS input provided.")
            return@Command
        }

        val text = arguments.joinToString(" ")

        try {
            logger.info("Playing TTS from message '$text'...")

            val url = httpClient.post<TtsResponse>("https://streamlabs.com/polly/speak") {
                contentType(ContentType.Application.Json)

                body = TtsRequest(
                    voice = "Brian",
                    text = text
                )
            }.speakUrl

            logger.info("Streamlabs returned URL '$url'.")

            withContext(Dispatchers.IO) {
                val ttsDataFile = File.createTempFile("tts_", ".mp3").apply {
                    writeBytes(httpClient.get<HttpResponse>(url).content.toInputStream().readAllBytes())
                }

                val ttsSpeechDuration = AudioFileIO.read(ttsDataFile).audioHeader.trackLength.seconds
                addedUserCooldown = ttsSpeechDuration * 5

                ttsFileQueue.add(TtsQueueEntry(ttsDataFile, ttsSpeechDuration))
            }
        } catch (e: Exception) {
            chat.sendMessage(BotConfig.channel, "Unable to play TTS.")
            logger.error("Unable to play TTS:", e)
        }
    }
)

val ttsPlayerCoroutineScope = CoroutineScope(Dispatchers.IO)

@Suppress("unused")
val ttsPlayerJob = ttsPlayerCoroutineScope.launch {
    while (isActive) {
        ttsFileQueue.removeFirstOrNull()?.let { entry ->
            entry.file.inputStream().use {
                Player(it).play()
            }

            delay(entry.duration + 3.seconds)
        } ?: run {
            delay(1.seconds)
        }
    }
}