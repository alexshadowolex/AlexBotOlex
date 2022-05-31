package commands

import BotConfig
import Command
import spotifyClient
import com.adamratzman.spotify.endpoints.pub.SearchApi
import com.adamratzman.spotify.models.Track
import com.adamratzman.spotify.utils.Market
import httpClient
import io.ktor.client.request.*
import io.ktor.http.*

val emotes = listOf("BLANKIES", "NODDERS", "ratJAM", "LETSFUCKINGO", "batPls", "borpafast", "breadyJAM", "AlienPls3", "DonaldPls", "pigeonJam")

val songRequestCommand = Command(
    names = listOf("sr", "songrequest"),
    handler = { arguments ->
        if (arguments.isEmpty()) {
            chat.sendMessage(BotConfig.channel, "No song given.")
            return@Command
        }

        val query = arguments.joinToString(" ")

        chat.sendMessage(
            BotConfig.channel,
            updateQueue(query)?.let { track ->
                putUserOnCooldown = true
                "Song '${track.name}' by ${track.artists.map { "'${it.name}'" }.let { artists ->
                    listOf(artists.dropLast(1).joinToString(), artists.last()).filter { it.isNotBlank() }.joinToString(" and ")}
                } has been added to the playlist ${emotes.random()}"
            } ?: run {
                putUserOnCooldown = false
                "No track with query '$query' found."
            }
        )
    }
)

suspend fun updateQueue(query: String): Track? {
    val result = Url(query).takeIf { it.host == "open.spotify.com" && it.encodedPath.startsWith("/track/") }?.let {
        spotifyClient.tracks.getTrack(it.encodedPath.substringAfter("/track/"))
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

    try {
        httpClient.post<Unit>("https://api.spotify.com/v1/me/player/queue") {
            url {
                parameters.append("uri", result.uri.uri)
            }
        }
    } catch (e: Exception) {
        println("WARNING: Spotify is probably not set up. Returning null...")
        return null
    }

    return result
}