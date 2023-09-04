package commands.spotify

import backgroundCoroutineScope
import com.adamratzman.spotify.models.Track
import com.github.twitch4j.chat.TwitchChat
import com.github.twitch4j.common.events.domain.EventUser
import config.SpotifyConfig
import config.TwitchBotConfig
import getCurrentSpotifySong
import handler.Command
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import logger
import resetSpotifyVolumeToDefault
import sendMessageToTwitchChatAndLogIt
import setSpotifyVolume
import ui.isSongLouderEnabled
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

val songLouderCommand: Command = Command(
    names = listOf("songlouder", "sl"),
    description = "Voting on making the current song louder. 2 options: \"${VOTE_OPTIONS.YES}\" or \"${VOTE_OPTIONS.NO}\". After ${SpotifyConfig.waitingTimeSongLouder} it will evaluate the votes. You need at least 3 votes and at least $FACTOR_MORE_YES_THAN_NO times more of the yes votes than no votes.",
    handler = { arguments ->
        if(!isSongLouderEnabled && TwitchBotConfig.channel != messageEvent.user.name) {
            sendMessageToTwitchChatAndLogIt(chat, "Song louder is disabled ${TwitchBotConfig.commandDisabledEmote1} Now suck my ${TwitchBotConfig.commandDisabledEmote2}")
            return@Command
        }

        if(arguments.isEmpty()) {
            sendMessageToTwitchChatAndLogIt(chat, "No vote given ${TwitchBotConfig.shrugEmote}")
            return@Command
        }
        val vote = arguments.first().lowercase()

        if(vote == VOTE_OPTIONS.YES || vote == VOTE_OPTIONS.NO) {
            if(Clock.System.now() < nextVoteTimeStart) {
                sendMessageToTwitchChatAndLogIt(chat, "Voting for making this song louder is possible in ${(nextVoteTimeStart - Clock.System.now()).inWholeSeconds.seconds}")
                return@Command
            }

            if(voteTimeEnd == null) {
                do {
                    currentSong = getCurrentSpotifySong()
                } while (currentSong == null)

                voteTimeEnd = Clock.System.now() + SpotifyConfig.waitingTimeSongLouder
                logger.info("Started voting for making louder. Variables - currentSong.name: ${currentSong?.name} | voteTimeEnd: $voteTimeEnd")
                startVoteController(chat)

                sendMessageToTwitchChatAndLogIt(chat, "Voting for making song louder started and will end in ${SpotifyConfig.waitingTimeSongLouder}. Type \"${TwitchBotConfig.commandPrefix}sl ${VOTE_OPTIONS.YES}\" or \"${TwitchBotConfig.commandPrefix}sl ${VOTE_OPTIONS.NO}\" to vote")
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
                    setSpotifyVolume(SpotifyConfig.songLouderIncreasedVolume)
                    startVolumeResetHandler(currentSong)
                    "Making song ${currentSong?.name} louder ${TwitchBotConfig.songRequestEmotes.random()}"
                } catch (e: Exception) {
                    logger.error("Making song louder failed, exception: ", e)
                    "Making the song louder failed, ty Spotify"
                }
            } else {
                logger.info("Had $amountYes yes votes and $amountNo no votes for making the song louder, which is only a factor of ${(amountYes / amountNo).toFloat()}, not $FACTOR_MORE_YES_THAN_NO")
                "Not enough ${VOTE_OPTIONS.YES} votes for making the song louder, need at least $FACTOR_MORE_YES_THAN_NO times more ${VOTE_OPTIONS.YES} votes. Can't do nothing ${TwitchBotConfig.shrugEmote}"
            }
        } else {
            logger.info("Only had ${amountYes + amountNo} total votes for making the song louder, not $MINIMUM_AMOUNT_VOTES")
            "Not enough total votes for making the song louder, need at least $MINIMUM_AMOUNT_VOTES votes. Can't do nothing ${TwitchBotConfig.shrugEmote}"
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

private fun startVolumeResetHandler(currentSong: Track?) {
    backgroundCoroutineScope.launch {
        do {
            delay(2.seconds)
        } while (currentSong == getCurrentSpotifySong())
        resetSpotifyVolumeToDefault()
    }
}