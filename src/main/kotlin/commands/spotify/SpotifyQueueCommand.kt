package commands.spotify

import config.TwitchBotConfig
import handler.Command

val spotifyQueueCommand: Command = Command(
    names = listOf("spotifyqueue", "sq"),
    description = "Displays the current spotify queue, which are the songs added via song requests.",
    handler = {

        chat.sendMessage(
            TwitchBotConfig.channel,
            "queue for this ${TwitchBotConfig.thisEmote}"
        )

        addedCommandCooldown = TwitchBotConfig.defaultCommandCooldown
    }
)