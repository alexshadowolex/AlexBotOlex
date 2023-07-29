package commands.spotify

import config.TwitchBotConfig
import handler.Command
import sendMessageToTwitchChatAndLogIt

val spotifyQueueCommand: Command = Command(
    names = listOf("spotifyqueue", "sq"),
    description = "Displays the current spotify queue, which are the songs added via song requests.",
    handler = {

        sendMessageToTwitchChatAndLogIt(
            chat,
            "queue for this ${TwitchBotConfig.thisEmote}"
        )

        addedCommandCoolDown = TwitchBotConfig.defaultCommandCoolDown
    }
)