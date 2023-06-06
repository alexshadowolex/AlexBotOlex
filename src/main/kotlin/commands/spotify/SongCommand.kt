package commands.spotify

import handler.Command
import config.TwitchBotConfig
import createSongString
import getCurrentSpotifySong

val songCommand: Command = Command(
    names = listOf("song", "s"),
    description = "Displays the current song name together with the spotify link.",
    handler = {
        val currentSong = getCurrentSpotifySong()
        val message = if(currentSong == null) {
            "No Song playing right now!"
        } else {
            createSongString(currentSong) + " --> ${currentSong.externalUrls.spotify}"
        }
        chat.sendMessage(
            TwitchBotConfig.channel,
            message
        )

        addedCommandCooldown = TwitchBotConfig.defaultCommandCooldown
    }
)