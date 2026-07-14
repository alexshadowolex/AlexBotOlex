package commands.twitchOnly

import backgroundCoroutineScope
import com.github.twitch4j.chat.TwitchChat
import config.GoogleSpreadSheetConfig
import config.TwitchBotConfig
import handler.Command
import isCommandDisabled
import kotlinx.coroutines.*
import logger
import me.xdrop.fuzzywuzzy.FuzzySearch
import sendCommandDisabledMessage
import sendMessageToTwitchChatAndLogIt
import ui.SwitchStateVariables
import java.io.File
import java.util.*
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
        val soundAlertFilesList = soundAlertDirectory.listFiles()!!
            .filter { it.extension in TwitchBotConfig.allowedSoundFiles }

        val soundAlertFile = if (query.isEmpty()) {
            soundAlertFilesList.random()
        } else {
            soundAlertFilesList.find {
                it.nameWithoutExtension == findSoundAlert(
                    soundAlertFilesList.map { args -> args.nameWithoutExtension },
                    query
                )
            }
        }

        if(soundAlertFile != null) {
            soundAlertQueue.add(soundAlertFile)

            handleSoundAlertReactions(soundAlertFile, chat)
        } else {
            sendMessageToTwitchChatAndLogIt(chat, "Mad bro? Couldn't find a fitting sound alert.")
            tmpUserCoolDown = 5.seconds
            tmpCommandCoolDown = 5.seconds
        }
        addedUserCoolDown = tmpUserCoolDown
        addedCommandCoolDown = tmpCommandCoolDown
    }
)


private const val MIN_TOKEN_SIMILARITY = 90
private const val MIN_TOKEN_SET_SCORE = 50
private const val MIN_FINAL_SCORE = 70.0

private fun findSoundAlert(
    soundAlertList: List<String>,
    query: String
): String? {

    val normalizedQuery = query.lowercase(Locale.getDefault())
    val inputTokens = tokenize(normalizedQuery)

    if (inputTokens.isEmpty()) {
        return null
    }

    var bestMatch: String? = null
    var bestScore = Double.MIN_VALUE

    for (soundAlert in soundAlertList) {

        val normalizedAlert = soundAlert.lowercase(Locale.getDefault())
        val alertTokens = tokenize(normalizedAlert)

        val tokenSetScore = FuzzySearch.tokenSetRatio(
            normalizedQuery,
            normalizedAlert
        )

        var matchedTokens = 0
        var similaritySum = 0

        for (inputToken in inputTokens) {

            val bestSimilarity = alertTokens.maxOfOrNull { alertToken ->

                val lengthRatio =
                    minOf(inputToken.length, alertToken.length).toDouble() /
                            maxOf(inputToken.length, alertToken.length)

                if (lengthRatio < 0.55) {
                    return@maxOfOrNull 0
                }

                if (commonCharacterRatio(inputToken, alertToken) < 0.45) {
                    return@maxOfOrNull 0
                }

                if (kotlin.math.abs(inputToken.length - alertToken.length) <= 2) {
                    FuzzySearch.ratio(inputToken, alertToken)
                } else {
                    FuzzySearch.partialRatio(inputToken, alertToken)
                }

            } ?: 0

            if (bestSimilarity >= MIN_TOKEN_SIMILARITY) {
                matchedTokens++
                similaritySum += bestSimilarity
            }
        }

        if (matchedTokens == 0 && tokenSetScore < MIN_TOKEN_SET_SCORE) {
            continue
        }

        val coverage = matchedTokens.toDouble() / inputTokens.size

        val averageSimilarity =
            if (matchedTokens == 0) {
                0.0
            } else {
                similaritySum.toDouble() / matchedTokens
            }

        val score =
            tokenSetScore * 0.35 +
            averageSimilarity * 0.25 +
            coverage * 100 * 0.40

        if (score > bestScore) {
            bestScore = score
            bestMatch = soundAlert
        }
    }

    return if (bestScore >= MIN_FINAL_SCORE) {
        bestMatch
    } else {
        null
    }
}

private fun tokenize(input: String): List<String> {
    return input
        .lowercase(Locale.getDefault())
        .split(Regex("\\W+"))
        .filter { it.isNotBlank() }
}

private fun commonCharacterRatio(a: String, b: String): Double {
    val setA = a.toSet()
    val setB = b.toSet()

    val common = setA.intersect(setB).size
    val max = maxOf(setA.size, setB.size)

    return common.toDouble() / max
}

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

private val soundAlertToReactionEmote = mapOf(
    "bustin.mp3" to "Bustin",
    "chipi chipi.mp3" to "chipichipi"
)
private suspend fun handleSoundAlertReactions(soundAlertFile: File, chat: TwitchChat) {
    val soundAlertName = soundAlertFile.name
    if(!soundAlertToReactionEmote.keys.contains(soundAlertName)) {
        return
    }

    val reactionEmote = soundAlertToReactionEmote[soundAlertName]
    logger.info("Sound alert $soundAlertName was played, issuing $reactionEmote!")

    delay(1.5.seconds)

    for (i in 1..6) {
        val amount = if(i <= 3) {
            i
        } else {
            abs(i - 6)
        }
        chat.sendMessage(TwitchBotConfig.channel, ("$reactionEmote ").repeat(amount))
        delay(0.5.seconds)
    }
}
