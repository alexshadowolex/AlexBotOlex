package handler

import ClipPlayerConfig
import json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import logger
import java.io.File

class ClipPlayerHandler private constructor(
    private val clips: Set<String>,
    playedClips: Set<String>,
    private val playListFile: File
) {
    companion object {
        val instance = run {
            val clipDirectory = File(ClipPlayerConfig.clipLocation)

            if (!clipDirectory.isDirectory) {
                logger.error("Clip directory doesn't exist. Please make sure to use the correct path.")
                return@run null
            }

            val playListFile = File("data\\saveData\\currentClipPlaylist.json")

            val playedClips = if (!playListFile.exists()) {
                playListFile.createNewFile()
                logger.info("Playlist file created.")
                setOf()
            } else {
                try {
                    json.decodeFromString<Set<String>>(playListFile.readText()).also { currentPlaylistData ->
                        logger.info("Existing playlist file found! Values: ${currentPlaylistData.joinToString(" | ")}")
                    }
                } catch (e: Exception) {
                    logger.warn("Error while reading playlist file. Initializing empty playlist", e)
                    setOf()
                }
            }

            val clips = clipDirectory.listFiles()!!
                .filter { it.extension in ClipPlayerConfig.allowedVideoFiles }
                .map { it.name }
                .toSet()

            if (clips.isEmpty()) {
                logger.error("No clips in folder ${ClipPlayerConfig.clipLocation}.")
                return@run null
            }

            logger.info("Clips in folder after applying playlist values ${ClipPlayerConfig.clipLocation}: ${clips.joinToString(" | ") { "$it: played = ${it in playedClips}" }}")

            ClipPlayerHandler(clips, playedClips, playListFile)
        }
    }

    val currentlyPlayingClip = MutableStateFlow<String?>(null)

    private var playedClips = playedClips
        private set(value) {
            field = value
            playListFile.writeText(json.encodeToString(field))
        }

    fun popNextRandomClip(): String {
        if (playedClips.size >= clips.size) {
            resetPlaylistFile()
        }

        return clips.filter { it !in playedClips }.random().also {
            playedClips = playedClips + it
            currentlyPlayingClip.value = it
        }
    }

    private fun resetPlaylistFile() {
        playedClips = setOf()
        logger.info("Resetting playlist...")
    }
}