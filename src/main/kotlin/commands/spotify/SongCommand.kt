package commands.spotify

import config.TwitchBotConfig
import createSongString
import getCurrentSpotifySong
import handler.Command
import sendMessageToTwitchChatAndLogIt
import ui.isSongCommandEnabled

val songCommand: Command = Command(
    names = listOf("song", "s"),
    description = "Displays the current song name together with the spotify link.",
    handler = {
        if(!isSongCommandEnabled && TwitchBotConfig.channel != messageEvent.user.name) {
            sendMessageToTwitchChatAndLogIt(chat, "Song command is disabled ${TwitchBotConfig.commandDisabledEmote1} Now suck my ${TwitchBotConfig.commandDisabledEmote2}")
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