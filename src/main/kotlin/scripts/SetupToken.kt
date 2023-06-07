package scripts

import com.adamratzman.spotify.SpotifyScope
import com.adamratzman.spotify.getSpotifyAuthorizationUrl
import com.adamratzman.spotify.spotifyClientApi
import config.SpotifyConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.Desktop
import java.io.File
import java.net.URI

suspend fun main() {
    val redirectUri = "https://www.example.com"

    @Suppress("BlockingMethodInNonBlockingContext")
    Desktop.getDesktop().browse(
        URI.create(
            getSpotifyAuthorizationUrl(
                scopes = SpotifyScope.values(),
                clientId = SpotifyConfig.spotifyClientId,
                redirectUri = redirectUri
            )
        )
    )

    print("Spotify code: ")
    val code = readln()

    val api = spotifyClientApi {
        credentials {
            clientId = SpotifyConfig.spotifyClientId
            clientSecret = SpotifyConfig.spotifyClientSecret
            this.redirectUri = redirectUri
        }

        authorization {
            authorizationCode = code
        }
    }.build()

    println("Token: " + Json.encodeToString(api.token))
    File("data\\tokens\\spotifyToken.json").writeText(Json.encodeToString(api.token))
}