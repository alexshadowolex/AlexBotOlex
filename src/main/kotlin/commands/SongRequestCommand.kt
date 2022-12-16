package commands

import BotConfig
import Command
import com.adamratzman.spotify.endpoints.pub.SearchApi
import com.adamratzman.spotify.models.Track
import com.adamratzman.spotify.utils.Market
import httpClient
import io.ktor.client.request.*
import io.ktor.http.*
import logger
import spotifyClient
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

private val emotes = listOf("BLANKIES", "NODDERS", "ratJAM", "LETSFUCKINGO", "batPls", "borpafast", "breadyJAM", "AlienPls3", "DonaldPls", "pigeonJam", "catJAM")

val songRequestCommand = Command(
    names = listOf("sr", "songrequest"),
    handler = { arguments ->
        if (arguments.isEmpty()) {
            chat.sendMessage(BotConfig.channel, "No song given.")
            logger.warn("No arguments given")
            return@Command
        }

        val query = arguments.joinToString(" ")

        try {
            chat.sendMessage(
                BotConfig.channel,
                updateQueue(query)?.let { track ->
                    addedUserCooldown = 30.seconds
                    "Song '${track.name}' by ${
                        track.artists.map { "'${it.name}'" }.let { artists ->
                            listOf(
                                artists.dropLast(1).joinToString(),
                                artists.last()
                            ).filter { it.isNotBlank() }.joinToString(" and ")
                        }
                    } has been added to the playlist ${emotes.random()}"
                } ?: run {
                    "No track with query '$query' found."
                }
            )
        } catch (e: Exception) {
            logger.error("Something went wrong with songrequests", e)
        }
    }
)

suspend fun updateQueue(query: String): Track? {
    val spotifyTokenIsValid: Boolean = try {
        spotifyClient.isTokenValid(makeTestRequest = false).isValid
    } catch (e: Exception){
        logger.warn("Token seems invalid.", e)
        false
    }

    logger.info("called updateQueue. Checking if token is Valid: $spotifyTokenIsValid")

    if (!spotifyTokenIsValid) {
        logger.info("Refreshing Spotify token...")
        logger.info("Current token: '${spotifyClient.token.accessToken}'")

        try {
            spotifyClient.refreshToken()
        } catch (e: Exception){
            logger.error("An error occured trying to refresh the token.", e)
            logger.info("New token: '${spotifyClient.token.accessToken}', expires at: ${DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(spotifyClient.token.expiresAt))}")
            return null
        }

        logger.info("Token has been refreshed.")
    }

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
        httpClient.post("https://api.spotify.com/v1/me/player/queue") {
            header("Authorization", "Bearer ${spotifyClient.token.accessToken}")

            url {
                parameters.append("uri", result.uri.uri)
            }
        }

        logger.info("Result URI: ${result.uri.uri}")
    } catch (e: Exception) {
        logger.error("Spotify is probably not set up.", e)
        return null
    }

    return result
}