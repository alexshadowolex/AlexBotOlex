package commands

import Command
import config.TwitchBotConfig

val queueCommand: Command = Command(
    names = listOf("queue", "q"),
    description = "Displays the current spotify queue, which are the songs added via song requests.",
    handler = {

        chat.sendMessage(
            TwitchBotConfig.channel,
            "queue for this ${TwitchBotConfig.thisEmote}"
        )

        addedCommandCooldown = TwitchBotConfig.defaultCommandCooldown
    }
)