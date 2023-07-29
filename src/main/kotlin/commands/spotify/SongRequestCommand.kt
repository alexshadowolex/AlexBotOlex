package commands.spotify

import com.adamratzman.spotify.endpoints.pub.SearchApi
import com.adamratzman.spotify.models.Track
import com.adamratzman.spotify.utils.Market
import config.SpotifyConfig
import config.TwitchBotConfig
import handler.Command
import io.ktor.http.*
import logger
import sendMessageToTwitchChatAndLogIt
import spotifyClient
import ui.isSongRequestEnabled
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

val songRequestCommand = Command(
    names = listOf("sr", "songrequest"),
    description = "Add a spotify song to the current queue. Either provide a name or a link. The links have to be spotify song links \"open.spotify.com/tracks\"",
    handler = { arguments ->
        if(!isSongRequestEnabled && TwitchBotConfig.channel != messageEvent.user.name) {
            sendMessageToTwitchChatAndLogIt(chat, "Song Requests are disabled ${TwitchBotConfig.commandDisabledEmote1} Now suck my ${TwitchBotConfig.commandDisabledEmote2}")
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
                updateQueue(query)?.let { track ->
                    addedUserCoolDown = 30.seconds
                    "Song '${track.name}' by ${
                        track.artists.map { "'${it.name}'" }.let { artists ->
                            listOf(
                                artists.dropLast(1).joinToString(),
                                artists.last()
                            ).filter { it.isNotBlank() }.joinToString(" and ")
                        }
                    } has been added to the queue ${TwitchBotConfig.songRequestEmotes.random()}"
                } ?: run {
                    "Couldn't add song to the queue. Either the song was longer than ${SpotifyConfig.maximumLengthSongRequest}, your query returned no result or something went wrong."
                }
            )

            addedCommandCoolDown = TwitchBotConfig.defaultCommandCoolDown
        } catch (e: Exception) {
            logger.error("Something went wrong with song request", e)
        }
    }
)

suspend fun updateQueue(query: String): Track? {
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
        } ?: return null
    } catch (e: Exception) {
        logger.error("Error while searching for track:", e)
        return null
    }

    logger.info("Result after search: $result")
    if(result.length.milliseconds > SpotifyConfig.maximumLengthSongRequest) {
        logger.info("Song length ${result.length / 60000f} was longer than ${SpotifyConfig.maximumLengthSongRequest}")
        return null
    }

    try {
        spotifyClient.player.addItemToEndOfQueue(result.uri)
        logger.info("Result URI: ${result.uri.uri}")
    } catch (e: Exception) {
        logger.error("Spotify is probably not set up.", e)
        return null
    }

    return result
}