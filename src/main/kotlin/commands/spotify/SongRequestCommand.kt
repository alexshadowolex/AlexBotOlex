package commands.spotify

import com.adamratzman.spotify.endpoints.pub.SearchApi
import com.adamratzman.spotify.models.Track
import com.adamratzman.spotify.utils.Market
import config.SpotifyConfig
import config.TwitchBotConfig
import handler.Command
import io.ktor.http.*
import isCommandDisabled
import logger
import sendCommandDisabledMessage
import sendMessageToTwitchChatAndLogIt
import spotifyClient
import ui.SwitchStateVariables
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

val songRequestCommand = Command(
    names = listOf("sr", "songrequest"),
    description = "Add a spotify song to the current queue. Either provide a name or a link. The links have to be spotify song links \"open.spotify.com/tracks\"",
    handler = { arguments ->
        if(isCommandDisabled(SwitchStateVariables.isSongRequestEnabled.value, messageEvent.user.name)) {
            sendCommandDisabledMessage("Song request command", chat)
            return@Command
        }
        if (arguments.isEmpty()) {
            sendMessageToTwitchChatAndLogIt(chat, "No song given.")
            return@Command
        }

        val query = arguments.joinToString(" ")

        try {
            sendMessageToTwitchChatAndLogIt(
                chat,
                updateQueue(query).let { response ->
                    val track = response.track
                    if(track != null) {
                        addedUserCoolDown = 30.seconds
                        "Song '${track.name}' by ${
                            track.artists.map { "'${it.name}'" }.let { artists ->
                                listOf(
                                    artists.dropLast(1).joinToString(),
                                    artists.last()
                                ).filter { it.isNotBlank() }.joinToString(" and ")
                            }
                        } has been added to the queue ${TwitchBotConfig.songRequestEmotes.random()}"
                    } else {
                        "Couldn't add song to the queue. ${response.songRequestResultExplanation}"
                    }
                }
            )

            addedCommandCoolDown = TwitchBotConfig.defaultCommandCoolDown
        } catch (e: Exception) {
            logger.error("Something went wrong with song request", e)
        }
    }
)

private suspend fun updateQueue(query: String): SongRequestResult {
    logger.info("called updateQueue.")

    val result = try {
        Url(query).takeIf { it.host == "open.spotify.com" && it.encodedPath.contains("/track/") }?.let {
            val songId = it.encodedPath.substringAfter("/track/")
            logger.info("Song ID from link: $songId")
            spotifyClient.tracks.getTrack(
                track = songId,
                market = Market.DE
            )
        } ?: run {
            spotifyClient.search.search(
                query = query,
                searchTypes = arrayOf(
                    SearchApi.SearchType.Artist,
                    SearchApi.SearchType.Album,
                    SearchApi.SearchType.Track
                ),
                market = Market.DE
            ).tracks?.firstOrNull()
        } ?: return SongRequestResult(
            track = null,
            songRequestResultExplanation = "No Result when searching for song."
        )
    } catch (e: Exception) {
        logger.error("Error while searching for track:", e)
        return SongRequestResult(
            track = null,
            songRequestResultExplanation = "Exception when accessing spotify endpoints for searching the song."
        )
    }

    logger.info("Result after search: $result")
    if(result.length.milliseconds > SpotifyConfig.maximumLengthSongRequest) {
        logger.info("Song length ${result.length / 60000f} was longer than ${SpotifyConfig.maximumLengthSongRequest}")
        return SongRequestResult(
            track = null,
            songRequestResultExplanation = "The song was longer than ${SpotifyConfig.maximumLengthSongRequest}."
        )
    }

    try {
        spotifyClient.player.addItemToEndOfQueue(result.uri)
        logger.info("Result URI: ${result.uri.uri}")
    } catch (e: Exception) {
        logger.error("Spotify is probably not set up.", e)
        return SongRequestResult(
            track = null,
            songRequestResultExplanation = "Adding the song to the playlist failed."
        )
    }

    return SongRequestResult(
        track = result,
        songRequestResultExplanation = "Successfully added the song to the playlist."
    )
}

private data class SongRequestResult(
    val track: Track?,
    val songRequestResultExplanation: String
)