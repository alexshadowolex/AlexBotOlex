package commands.spotify

import config.TwitchBotConfig
import handler.Command
import isCommandDisabled
import sendCommandDisabledMessage
import sendMessageToTwitchChatAndLogIt
import ui.SwitchStateVariables

val spotifyQueueCommand: Command = Command(
    names = listOf("spotifyqueue", "sq"),
    description = "Displays the current spotify queue, which are the songs added via song requests.",
    handler = {
        if(isCommandDisabled(SwitchStateVariables.isSpotifyQueueEnabled.value, messageEvent.user.name)) {
            sendCommandDisabledMessage("Spotify queue command", chat)
            return@Command
        }

        sendMessageToTwitchChatAndLogIt(
            chat,
            "queue for this ${TwitchBotConfig.thisEmote}"
        )

        addedCommandCoolDown = TwitchBotConfig.defaultCommandCoolDown
    }
)