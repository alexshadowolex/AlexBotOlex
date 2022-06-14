package commands

import BotConfig
import Command
import httpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import javazoom.jl.player.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import logger
import javax.sound.sampled.AudioSystem
import kotlin.time.Duration.Companion.seconds

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
                val ttsDataInputStream = httpClient.get<HttpResponse>(url).content.toInputStream().buffered()
                val ttsSpeechDuration = (AudioSystem.getAudioFileFormat(ttsDataInputStream).properties()["duration"] as Long).seconds

                addedUserCooldown = ttsSpeechDuration * 5

                Player(ttsDataInputStream).play()
            }
        } catch (e: Exception) {
            chat.sendMessage(BotConfig.channel, "Unable to play TTS.")
            logger.error("Unable to play TTS:", e)
        }
    }
)