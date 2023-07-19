package config

import java.io.File
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object SpotifyConfig {
    private val properties = Properties().apply {
        load(File("data\\properties\\spotifyConfig.properties").inputStream())
    }

    val spotifyClientSecret: String = File("data\\tokens\\spotifyClientSecret.txt").readText()
    val spotifyClientId: String = properties.getProperty("spotify_client_id")
    val maximumLengthSongRequest: Duration = properties.getProperty("maximum_length_song_request").toInt().minutes
    val waitingTimeVoteSkip: Duration = properties.getProperty("waiting_time_vote_skip").toInt().seconds
}