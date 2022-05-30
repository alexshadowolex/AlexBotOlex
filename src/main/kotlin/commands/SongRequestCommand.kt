package commands

import BotConfig
import Command
import api
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
                "Song '${track.name}' by ${track.artists.joinToString { it.name }} added to the playlist ${emotes.random()}"
            } ?: run {
                putUserOnCooldown = false
                "No track with query '$query' found."
            }
        )
    }
)

suspend fun updateQueue(query: String): Track? {
    val result = Url(query).takeIf { it.host == "open.spotify.com" && it.encodedPath.startsWith("/track/") }?.let {
        api.tracks.getTrack(it.encodedPath.substringAfter("/track/"))
    } ?: run {
        api.search.search(
            query = query,
            searchTypes = arrayOf(
                SearchApi.SearchType.ARTIST,
                SearchApi.SearchType.ALBUM,
                SearchApi.SearchType.TRACK
            ),
            market = Market.DE
        ).tracks?.firstOrNull()
    } ?: return null

    httpClient.post<Unit>("https://api.spotify.com/v1/me/player/queue") {
        url {
            parameters.append("uri", result.uri.uri)
        }
    }

    return result
}