package commands.twitchOnly

import config.TwitchBotConfig
import handler.Command

val raidMessageCommand: Command = Command(
    names = listOf("raidmessage", "rm"),
    description = "Only meant to be used by streamer. Creates a raid message looping \"${TwitchBotConfig.raidMessageText}\" with the input given.",
    handler = { arguments ->
        if(messageEvent.user.name != TwitchBotConfig.channel) {
            return@Command
        }

        chat.sendMessage(
            TwitchBotConfig.channel,
            "${TwitchBotConfig.raidMessageText} ${arguments.joinToString(" ")} ".repeat(TwitchBotConfig.raidMessageAmountRepetitions)
        )
    }
)