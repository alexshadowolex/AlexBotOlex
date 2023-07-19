package commands.spotify

import com.adamratzman.spotify.models.Track
import com.github.twitch4j.chat.TwitchChat
import com.github.twitch4j.common.events.domain.EventUser
import config.SpotifyConfig
import config.TwitchBotConfig
import getCurrentSpotifySong
import handler.Command
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import logger
import spotifyClient
import kotlin.time.Duration.Companion.seconds

private val VOTE_OPTIONS = object {
    val YES = "yes"
    val NO = "no"
}
private val VOTING_TIME = 15.seconds
private val COOL_DOWN_AFTER_VOTING = 10.seconds
private const val MINIMUM_AMOUNT_VOTES = 3
private const val FACTOR_MORE_YES_THAN_NO = 1.5

private var currentVotesPerUser = mutableMapOf<EventUser, String>()
private var voteTimeEnd: Instant? = null
private var nextVoteTimeStart: Instant = Clock.System.now()
private var currentSong: Track? = null

val voteSkipCommand: Command = Command(
    names = listOf("voteskip", "vs"),
    description = "Voting on skipping the current song. 2 options: \"${VOTE_OPTIONS.YES}\" or \"${VOTE_OPTIONS.NO}\". After ${SpotifyConfig.waitingTimeVoteSkip} it will evaluate the votes. You need at least 3 votes and at least $FACTOR_MORE_YES_THAN_NO times more of the yes votes than no votes.",
    handler = {arguments ->
        if(arguments.isEmpty()) {
            logger.info("Arguments were empty")
            chat.sendMessage(TwitchBotConfig.channel, "No vote given ${TwitchBotConfig.shrugEmote}")
            return@Command
        }
        val vote = arguments.first().lowercase()

        if(vote == VOTE_OPTIONS.YES || vote == VOTE_OPTIONS.NO) {
            if(Clock.System.now() < nextVoteTimeStart) {
                logger.info("Voting is still on cool down")
                chat.sendMessage(TwitchBotConfig.channel, "Voting for this song is possible in ${(nextVoteTimeStart - Clock.System.now()).inWholeSeconds.seconds}")
                return@Command
            }

            if(voteTimeEnd == null) {
                do {
                    currentSong = getCurrentSpotifySong()
                } while (currentSong == null)

                voteTimeEnd = Clock.System.now() + VOTING_TIME
                logger.info("Started voting for skip. Variables - currentSong.name: ${currentSong?.name} | voteTimeEnd: $voteTimeEnd")
                startVoteController(chat)

                chat.sendMessage(TwitchBotConfig.channel, "Voting started and will end in $VOTING_TIME")
            }

            currentVotesPerUser[messageEvent.user] = vote
            logger.info("Updated currentVotesPerUser, new values: ${currentVotesPerUser.map { it.key.name + ": " + it.value }}")
        } else {
            logger.info("Input $vote was not valid")
            chat.sendMessage(TwitchBotConfig.channel, "Invalid input for voting option!")
        }
    }
)

private suspend fun startVoteController(chat: TwitchChat) {
    CoroutineScope(Dispatchers.IO).launch {
        while (Clock.System.now() < voteTimeEnd!!) {
            if(getCurrentSpotifySong() != currentSong) {
                logger.info("Song changed before vote time was over, aborted.")
                chat.sendMessage(TwitchBotConfig.channel, "Song ended, voting aborted")
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
                    logger.info("Skipping song")
                    spotifyClient.player.skipForward()
                    "Skipping song ${currentSong?.name}"
                } catch (e: Exception) {
                    logger.error("Skipping song failed, exception: ", e)
                    "Skipping the song failed, ty Spotify"
                }
            } else {
                logger.info("Had $amountYes yes votes and $amountNo no votes, which is only a factor of ${(amountYes / amountNo).toFloat()}, not $FACTOR_MORE_YES_THAN_NO")
                "Not enough ${VOTE_OPTIONS.YES} votes, need at least $FACTOR_MORE_YES_THAN_NO times more ${VOTE_OPTIONS.YES} votes. Can't do nothing ${TwitchBotConfig.shrugEmote}"
            }
        } else {
            logger.info("Only had ${amountYes + amountNo} total votes, not $MINIMUM_AMOUNT_VOTES")
            "Not enough total votes, need at least $MINIMUM_AMOUNT_VOTES votes. Can't do nothing ${TwitchBotConfig.shrugEmote}"
        }
        chat.sendMessage(TwitchBotConfig.channel, message)
        resetVotingVariables()
    }
}

private fun resetVotingVariables() {
    voteTimeEnd = null
    currentSong = null
    currentVotesPerUser = mutableMapOf()
    nextVoteTimeStart = Clock.System.now() + COOL_DOWN_AFTER_VOTING
}