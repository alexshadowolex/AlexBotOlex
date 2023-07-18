package commands.twitchOnly

import com.github.twitch4j.chat.TwitchChat
import config.GoogleSpreadSheetConfig
import config.TwitchBotConfig
import handler.Command
import kotlinx.coroutines.*
import logger
import org.apache.commons.text.similarity.LevenshteinDistance
import ui.isSoundAlertEnabled
import java.io.File
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

private val soundAlertQueue = mutableListOf<File>()

val soundAlertCommand: Command = Command(
    names = listOf("soundalert", "sa"),
    description = "Activate a sound alert. Following sound alerts exist: ${GoogleSpreadSheetConfig.soundAlertSpreadSheetLink}",
    handler = {arguments ->
        if(!isSoundAlertEnabled && TwitchBotConfig.channel != messageEvent.user.name) {
            logger.info("Sound Alerts are disabled, aborting command execution.")
            chat.sendMessage(TwitchBotConfig.channel, "Sound Alerts are disabled ${TwitchBotConfig.commandDisabledEmote1} Now suck my ${TwitchBotConfig.commandDisabledEmote2}")
            return@Command
        }
        val soundAlertDirectory = File(TwitchBotConfig.soundAlertDirectory)

        if (!soundAlertDirectory.isDirectory) {
            logger.error("Sound alert directory doesn't exist. Please make sure to use the correct path.")
            return@Command
        }

        val query = arguments.joinToString(" ").lowercase()

        var tmpUserCooldown = TwitchBotConfig.defaultUserCooldown
        var tmpCommandCooldown = TwitchBotConfig.defaultCommandCooldown
        val soundAlertFile = if (query.isEmpty()) {
            soundAlertDirectory.listFiles()!!
                .filter { it.extension in TwitchBotConfig.allowedSoundFiles }
                .random()
        } else {
            soundAlertDirectory.listFiles()!!
                .filter { it.extension in TwitchBotConfig.allowedSoundFiles }
                .map { it to LevenshteinDistance.getDefaultInstance().apply(it.nameWithoutExtension.lowercase(), query) }
                .minByOrNull { (_, levenshteinDistance) -> levenshteinDistance }
                ?.takeIf { (_, levenshteinDistance) -> levenshteinDistance < TwitchBotConfig.levenshteinThreshold }
                ?.first
        }

        if(soundAlertFile != null) {
            soundAlertQueue.add(soundAlertFile)

            handleBustinSoundAlert(soundAlertFile, chat)
        } else {
            chat.sendMessage(TwitchBotConfig.channel, "Mad bro? Couldn't find a fitting sound alert.")
            tmpUserCooldown = 5.seconds
            tmpCommandCooldown = 5.seconds
        }
        addedUserCooldown = tmpUserCooldown
        addedCommandCooldown = tmpCommandCooldown
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

private const val BUSTIN_SOUND_ALERT_NAME = "bustin.mp3"
private suspend fun handleBustinSoundAlert(soundAlertFile: File, chat: TwitchChat) {
    val bustinSoundAlertFile = File(TwitchBotConfig.soundAlertDirectory + "\\$BUSTIN_SOUND_ALERT_NAME")
    if(!bustinSoundAlertFile.exists()) {
        return
    }

    if(soundAlertFile == bustinSoundAlertFile) {
        val bustinEmote = "Bustin "
        delay(1.5.seconds)

        for (i in 1..6) {
            val amount = if(i <= 3) {
                i
            } else {
                abs(i - 6)
            }
            chat.sendMessage(TwitchBotConfig.channel, bustinEmote.repeat(amount))
            delay(0.5.seconds)
        }
    }
}
