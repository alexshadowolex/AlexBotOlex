package commands.twitchOnly

import config.TwitchBotConfig
import handler.Command
import sendMessageToTwitchChatAndLogIt

var firstUser: String? = null

val firstCommand: Command = Command(
    names = listOf("first"),
    description = "First user to use this command will be displayed for the rest of the bot's runtime as first.",
    handler = {
        val message = if(firstUser == null) {
            firstUser = messageEvent.user.name
            "${messageEvent.user.name} is the fastest and claimed first ${TwitchBotConfig.pepeVibeHardEmote}"
        } else {
            if(firstUser == messageEvent.user.name) {
                "Yes, you are the fastest. Stop self praising ${TwitchBotConfig.yikersEmote}"
            } else {
                "Too late, $firstUser was faster ${TwitchBotConfig.shrugEmote}"
            }
        }

        sendMessageToTwitchChatAndLogIt(
            chat,
            message
        )
    }
)