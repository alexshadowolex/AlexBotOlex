package commands.twitchOnly

import config.TwitchBotConfig
import handler.Command
import logger
import sendMessageToTwitchChatAndLogIt

val raidMessageCommand: Command = Command(
    names = listOf("raidmessage", "rm"),
    description = "Only meant to be used by streamer. Creates a raid message looping \"${TwitchBotConfig.raidMessageText}\" with the input given.",
    handler = { arguments ->
        if(messageEvent.user.name != TwitchBotConfig.channel) {
            logger.info("User is not privileged to use command")
            return@Command
        }

        sendMessageToTwitchChatAndLogIt(
            chat,
            "${TwitchBotConfig.raidMessageText} ${arguments.joinToString(" ")} ".repeat(TwitchBotConfig.raidMessageAmountRepetitions)
        )
    }
)