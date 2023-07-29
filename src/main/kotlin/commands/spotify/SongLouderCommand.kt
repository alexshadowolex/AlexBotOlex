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
import spotifyClient
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
                chat.sendMessage(TwitchBotConfig.channel, "Voting for this making this song louder is possible in ${(nextVoteTimeStart - Clock.System.now()).inWholeSeconds.seconds}")
                return@Command
            }

            if(voteTimeEnd == null) {
                do {
                    currentSong = getCurrentSpotifySong()
                } while (currentSong == null)

                voteTimeEnd = Clock.System.now() + SpotifyConfig.waitingTimeSongLouder
                logger.info("Started voting for making louder. Variables - currentSong.name: ${currentSong?.name} | voteTimeEnd: $voteTimeEnd")
                startVoteController(chat)

                chat.sendMessage(TwitchBotConfig.channel, "Voting for making song louder started and will end in ${SpotifyConfig.waitingTimeSongLouder}. Type \"${TwitchBotConfig.commandPrefix}sl ${VOTE_OPTIONS.YES}\" or \"${TwitchBotConfig.commandPrefix}sl ${VOTE_OPTIONS.NO}\" to vote")
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
    backgroundCoroutineScope.launch {
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
                    logger.info("Making song louder")
                    spotifyClient.player.setVolume(SpotifyConfig.songLouderIncreasedVolume)
                    startVolumeResetHandler(currentSong)
                    "Making song ${currentSong?.name} louder ${TwitchBotConfig.peepoDjEmote}"
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
        chat.sendMessage(TwitchBotConfig.channel, message)
        resetVotingVariables()
    }
}

private fun resetVotingVariables() {
    voteTimeEnd = null
    currentSong = null
    currentVotesPerUser = mutableMapOf()
    nextVoteTimeStart = Clock.System.now() + SpotifyConfig.cooldownAfterVoting
}

private fun startVolumeResetHandler(currentSong: Track?) {
    backgroundCoroutineScope.launch {
        do {
            delay(2.seconds)
        } while (currentSong == getCurrentSpotifySong())
        spotifyClient.player.setVolume(SpotifyConfig.defaultVolume)
    }
}