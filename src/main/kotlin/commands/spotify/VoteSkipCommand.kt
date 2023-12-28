package commands.spotify

import backgroundCoroutineScope
import com.adamratzman.spotify.models.Track
import com.github.twitch4j.chat.TwitchChat
import com.github.twitch4j.common.events.domain.EventUser
import config.SpotifyConfig
import config.TwitchBotConfig
import getCurrentSpotifySong
import handler.Command
import isCommandDisabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import logger
import sendCommandDisabledMessage
import sendMessageToTwitchChatAndLogIt
import spotifyClient
import ui.SwitchStateVariables
import kotlin.time.Duration.Companion.seconds

private val VOTE_OPTIONS = object {
    val YES = "yes"
    val NO = "no"
}

private const val MINIMUM_AMOUNT_VOTES = 3
private const val FACTOR_MORE_YES_THAN_NO = 1.5

private var currentVotesPerUser = mutableMapOf<EventUser, String>()
private var voteTimeEnd: Instant? = null
private var nextVoteTimeStart: Instant = Clock.System.now()
private var currentSong: Track? = null

val voteSkipCommand: Command = Command(
    names = listOf("voteskip", "vs"),
    description = "Voting on skipping the current song. 2 options: \"${VOTE_OPTIONS.YES}\" or \"${VOTE_OPTIONS.NO}\". After ${SpotifyConfig.waitingTimeVoteSkip} it will evaluate the votes. You need at least $MINIMUM_AMOUNT_VOTES votes and at least $FACTOR_MORE_YES_THAN_NO times more of the yes votes than no votes.",
    handler = {arguments ->
        if(isCommandDisabled(SwitchStateVariables.isVoteSkipEnabled.value, messageEvent.user.name)) {
            sendCommandDisabledMessage("Vote skip command", chat)
            return@Command
        }

        if(arguments.isEmpty()) {
            sendMessageToTwitchChatAndLogIt(chat, "No vote given ${TwitchBotConfig.shrugEmote}")
            return@Command
        }
        val vote = arguments.first().lowercase()

        if(vote == VOTE_OPTIONS.YES || vote == VOTE_OPTIONS.NO) {
            if(Clock.System.now() < nextVoteTimeStart) {
                sendMessageToTwitchChatAndLogIt(chat, "Voting for skipping this song is possible in ${(nextVoteTimeStart - Clock.System.now()).inWholeSeconds.seconds}")
                return@Command
            }

            if(voteTimeEnd == null) {
                do {
                    currentSong = getCurrentSpotifySong()
                } while (currentSong == null)

                voteTimeEnd = Clock.System.now() + SpotifyConfig.waitingTimeVoteSkip
                logger.info("Started voting for skip. Variables - currentSong.name: ${currentSong?.name} | voteTimeEnd: $voteTimeEnd")
                startVoteController(chat)

                sendMessageToTwitchChatAndLogIt(chat, "Voting for skip started and will end in ${SpotifyConfig.waitingTimeVoteSkip}. Type \"${TwitchBotConfig.commandPrefix}vs ${VOTE_OPTIONS.YES}\" or \"${TwitchBotConfig.commandPrefix}vs ${VOTE_OPTIONS.NO}\" to vote")
            }

            currentVotesPerUser[messageEvent.user] = vote
            logger.info("Updated currentVotesPerUser, new values: ${currentVotesPerUser.map { it.key.name + ": " + it.value }}")
        } else {
            sendMessageToTwitchChatAndLogIt(chat, "Invalid input for voting option!")
        }
    }
)

private suspend fun startVoteController(chat: TwitchChat) {
    backgroundCoroutineScope.launch {
        while (Clock.System.now() < voteTimeEnd!!) {
            if(getCurrentSpotifySong() != currentSong) {
                sendMessageToTwitchChatAndLogIt(chat, "Song ended, voting aborted")
                resetVotingVariables()
                return@launch
            }
            delay(0.5.seconds)
        }
        val amountYes = currentVotesPerUser.count { it.value == VOTE_OPTIONS.YES }
        val amountNo = currentVotesPerUser.count { it.value == VOTE_OPTIONS.NO }

        val message = if(amountYes + amountNo >= MINIMUM_AMOUNT_VOTES) {
            if(amountYes >= amountNo * 1.5) {
                try {
                    spotifyClient.player.skipForward()
                    "Skipping song ${currentSong?.name}"
                } catch (e: Exception) {
                    logger.error("Skipping song failed, exception: ", e)
                    "Skipping the song failed, ty Spotify"
                }
            } else {
                logger.info("Had $amountYes yes votes and $amountNo no votes for skipping, which is only a factor of ${(amountYes / amountNo).toFloat()}, not $FACTOR_MORE_YES_THAN_NO")
                "Not enough ${VOTE_OPTIONS.YES} votes for skipping, need at least $FACTOR_MORE_YES_THAN_NO times more ${VOTE_OPTIONS.YES} votes. Can't do nothing ${TwitchBotConfig.shrugEmote}"
            }
        } else {
            logger.info("Only had ${amountYes + amountNo} total votes for skipping, not $MINIMUM_AMOUNT_VOTES")
            "Not enough total votes for skipping, need at least $MINIMUM_AMOUNT_VOTES votes. Can't do nothing ${TwitchBotConfig.shrugEmote}"
        }
        sendMessageToTwitchChatAndLogIt(chat, message)
        resetVotingVariables()
    }
}

private fun resetVotingVariables() {
    voteTimeEnd = null
    currentSong = null
    currentVotesPerUser = mutableMapOf()
    nextVoteTimeStart = Clock.System.now() + SpotifyConfig.coolDownAfterVoting
}