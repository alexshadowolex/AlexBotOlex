package commands.spotify

import config.TwitchBotConfig
import handler.Command
import sendMessageToTwitchChatAndLogIt
import ui.isSpotifyQueueEnabled

val spotifyQueueCommand: Command = Command(
    names = listOf("spotifyqueue", "sq"),
    description = "Displays the current spotify queue, which are the songs added via song requests.",
    handler = {
        if(!isSpotifyQueueEnabled && TwitchBotConfig.channel != messageEvent.user.name) {
            sendMessageToTwitchChatAndLogIt(chat, "Spotify Queue is disabled ${TwitchBotConfig.commandDisabledEmote1} Now suck my ${TwitchBotConfig.commandDisabledEmote2}")
            return@Command
        }

        sendMessageToTwitchChatAndLogIt(
            chat,
            "queue for this ${TwitchBotConfig.thisEmote}"
        )

        addedCommandCoolDown = TwitchBotConfig.defaultCommandCoolDown
    }
)