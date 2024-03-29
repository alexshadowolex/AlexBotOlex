package commands.spotify

import config.TwitchBotConfig
import createSongString
import getCurrentSpotifySong
import handler.Command
import isCommandDisabled
import sendCommandDisabledMessage
import sendMessageToTwitchChatAndLogIt
import ui.SwitchStateVariables

val songCommand: Command = Command(
    names = listOf("song", "s"),
    description = "Displays the current song name together with the spotify link.",
    handler = {
        if(isCommandDisabled(SwitchStateVariables.isSongCommandEnabled.value, messageEvent.user.name)) {
            sendCommandDisabledMessage("Song command", chat)
            return@Command
        }

        val currentSong = getCurrentSpotifySong()
        val message = if(currentSong == null) {
            "No Song playing right now!"
        } else {
            createSongString(currentSong) + " --> ${currentSong.externalUrls.spotify}"
        }

        sendMessageToTwitchChatAndLogIt(
            chat,
            message
        )

        addedCommandCoolDown = TwitchBotConfig.defaultCommandCoolDown
    }
)