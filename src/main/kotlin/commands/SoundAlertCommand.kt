package commands

import Command
import config.TwitchBotConfig
import kotlinx.coroutines.*
import logger
import org.apache.commons.text.similarity.LevenshteinDistance
import java.io.File
import kotlin.time.Duration.Companion.seconds

private val soundAlertQueue = mutableListOf<File>()

val soundAlertCommand: Command = Command(
    names = listOf("soundalert", "sa"),
    description = """
            Activate a sound alert. Following sound alerts exist:
            ${
                File(TwitchBotConfig.soundAlertDirectory).listFiles()
                    ?.filter { it.extension in TwitchBotConfig.allowedSoundFiles }
                    ?.joinToString(",") { it.nameWithoutExtension }
            }
        """.trimIndent(),
    handler = {arguments ->
        val soundAlertDirectory = File(TwitchBotConfig.soundAlertDirectory)

        if (!soundAlertDirectory.isDirectory) {
            logger.error("Sound alert directory doesn't exist. Please make sure to use the correct path.")
            return@Command
        }

        val query = arguments.joinToString(" ").lowercase()


        if (query.isEmpty()) {
            soundAlertQueue.add(
                soundAlertDirectory.listFiles()!!
                    .filter { it.extension in TwitchBotConfig.allowedSoundFiles }
                    .random()
            )
            addedUserCooldown = TwitchBotConfig.userCooldown
        } else {
            val soundAlertFile = soundAlertDirectory.listFiles()!!
                .filter { it.extension in TwitchBotConfig.allowedSoundFiles }
                .map { it to LevenshteinDistance.getDefaultInstance().apply(it.nameWithoutExtension.lowercase(), arguments.joinToString(" ")) }
                .minByOrNull { (_, levenshteinDistance) -> levenshteinDistance }
                ?.takeIf { (_, levenshteinDistance) -> levenshteinDistance < TwitchBotConfig.levenshteinThreshold }
                ?.first

            soundAlertFile?.let {
                soundAlertQueue.add(it)
                addedUserCooldown = TwitchBotConfig.userCooldown
            } ?: run {
                chat.sendMessage(TwitchBotConfig.channel, "Mad bro? Couldn't find a fitting sound alert.")
                addedUserCooldown = 5.seconds
            }
        }
    }
)

private val soundAlertPlayerCoroutineScope = CoroutineScope(Dispatchers.IO)

@Suppress("unused")
val soundAlertPlayerJob = soundAlertPlayerCoroutineScope.launch {
    while (isActive) {
        val entry = soundAlertQueue.removeFirstOrNull()

        if (entry != null) {
            val soundProcess = withContext(Dispatchers.IO) {
                ProcessBuilder("ffplay", "-nodisp", "-autoexit", "-i", entry.absolutePath.replace("\\", "\\\\")).apply {
                    inheritIO()
                }.start()
            }

            while (soundProcess!!.isAlive) {
                supervisorScope {
                    try {
                        delay(0.1.seconds)
                    } catch (_: Exception) {
                        soundProcess.destroyForcibly()
                    }
                }
            }

            delay(3.seconds)
        } else {
            delay(1.seconds)
        }
    }
}