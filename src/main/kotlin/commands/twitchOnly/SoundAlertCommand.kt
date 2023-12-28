package commands.twitchOnly

import backgroundCoroutineScope
import com.github.twitch4j.chat.TwitchChat
import config.GoogleSpreadSheetConfig
import config.TwitchBotConfig
import handler.Command
import isCommandDisabled
import kotlinx.coroutines.*
import logger
import org.apache.commons.text.similarity.LevenshteinDistance
import sendCommandDisabledMessage
import sendMessageToTwitchChatAndLogIt
import ui.SwitchStateVariables
import java.io.File
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

private val soundAlertQueue = mutableListOf<File>()

val soundAlertCommand: Command = Command(
    names = listOf("soundalert", "sa"),
    description = "Activate a sound alert. Following sound alerts exist: ${GoogleSpreadSheetConfig.soundAlertSpreadSheetLink}",
    handler = {arguments ->
        if(isCommandDisabled(SwitchStateVariables.isSoundAlertEnabled.value, messageEvent.user.name)) {
            sendCommandDisabledMessage("Sound alert command", chat)
            return@Command
        }
        val soundAlertDirectory = File(TwitchBotConfig.soundAlertDirectory)

        if (!soundAlertDirectory.isDirectory) {
            logger.error("Sound alert directory doesn't exist. Please make sure to use the correct path.")
            return@Command
        }

        val query = arguments.joinToString(" ").lowercase()

        var tmpUserCoolDown = TwitchBotConfig.defaultUserCoolDown
        var tmpCommandCoolDown = TwitchBotConfig.defaultCommandCoolDown

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
            sendMessageToTwitchChatAndLogIt(chat, "Mad bro? Couldn't find a fitting sound alert.")
            tmpUserCoolDown = 5.seconds
            tmpCommandCoolDown = 5.seconds
        }
        addedUserCoolDown = tmpUserCoolDown
        addedCommandCoolDown = tmpCommandCoolDown
    }
)

@Suppress("unused")
val soundAlertPlayerJob = backgroundCoroutineScope.launch {
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
        logger.info("Sound alert bustin was played, issuing bustin!")
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
