package commands

import Command
import com.adamratzman.spotify.endpoints.pub.SearchApi
import com.adamratzman.spotify.models.Track
import com.adamratzman.spotify.utils.Market
import config.TwitchBotConfig
import httpClient
import io.ktor.client.request.*
import io.ktor.http.*
import logger
import spotifyClient
import kotlin.time.Duration.Companion.seconds

val songRequestCommand = Command(
    names = listOf("sr", "songrequest"),
    description = "Add a spotify song to the current queue. Either provide a name or a link. The links have to be spotify song links \"open.spotify.com/tracks\"",
    handler = { arguments ->
        if (arguments.isEmpty()) {
            chat.sendMessage(TwitchBotConfig.channel, "No song given.")
            logger.warn("No arguments given")
            return@Command
        }

        val query = arguments.joinToString(" ")

        try {
            chat.sendMessage(
                TwitchBotConfig.channel,
                updateQueue(query)?.let { track ->
                    addedUserCooldown = 30.seconds
                    "Song '${track.name}' by ${
                        track.artists.map { "'${it.name}'" }.let { artists ->
                            listOf(
                                artists.dropLast(1).joinToString(),
                                artists.last()
                            ).filter { it.isNotBlank() }.joinToString(" and ")
                        }
                    } has been added to the queue ${TwitchBotConfig.songRequestEmotes.random()}"
                } ?: run {
                    "Couldn't add song to the queue. Either something went wrong or your query returned no result."
                }
            )

            addedCommandCooldown = TwitchBotConfig.defaultCommandCooldown
        } catch (e: Exception) {
            logger.error("Something went wrong with songrequests", e)
        }
    }
)

suspend fun updateQueue(query: String): Track? {
    logger.info("called updateQueue.")

    val result = try {
        Url(query).takeIf { it.host == "open.spotify.com" && it.encodedPath.startsWith("/track/") }?.let {
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
                    SearchApi.SearchType.ARTIST,
                    SearchApi.SearchType.ALBUM,
                    SearchApi.SearchType.TRACK
                ),
                market = Market.DE
            ).tracks?.firstOrNull()
        } ?: return null
    } catch (e: Exception) {
        logger.error("Error while searching for track:", e)
        return null
    }

    logger.info("Result after search: $result")

    try {
        val responseStatusCode = httpClient.post("https://api.spotify.com/v1/me/player/queue") {
            header("Authorization", "Bearer ${spotifyClient.token.accessToken}")

            url {
                parameters.append("uri", result.uri.uri)
            }
        }.status

        if(responseStatusCode != HttpStatusCode.NoContent) {
            logger.error("HTTP Response was not 204, something went wrong.")
            return null
        }
        logger.info("Result URI: ${result.uri.uri}")
    } catch (e: Exception) {
        logger.error("Spotify is probably not set up.", e)
        return null
    }

    return result
}