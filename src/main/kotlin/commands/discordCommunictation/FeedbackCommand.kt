package commands.discordCommunictation

import DiscordBotConfig
import DiscordMessageContent
import config.TwitchBotConfig
import handler.Command
import sendMessageToDiscordBot
import sendMessageToTwitchChatAndLogIt
import ui.isFeedbackEnabled
import kotlin.time.Duration.Companion.seconds

val feedbackCommand: Command = Command(
    names = listOf("fb", "feedback"),
    description = "Automatically posts the given message in the feedback channel on Discord.",
    handler = { arguments ->
        if(!isFeedbackEnabled && TwitchBotConfig.channel != messageEvent.user.name) {
            sendMessageToTwitchChatAndLogIt(chat, "Feedback is disabled ${TwitchBotConfig.commandDisabledEmote1} Now suck my ${TwitchBotConfig.commandDisabledEmote2}")
            return@Command
        }

        val message = arguments.joinToString(" ")
        if (message.trim().isEmpty()) {
            sendMessageToTwitchChatAndLogIt(chat, "No input has been provided ${TwitchBotConfig.rejectEmote}")
            addedUserCoolDown = 5.seconds
            return@Command
        }

        val currentMessageContent = DiscordMessageContent(
            message = DiscordMessageContent.Message.FromText(message),
            title = "Suggestion for ",
            user = messageEvent.user.name,
            channelId = DiscordBotConfig.feedbackChannelId
        )

        val channel = sendMessageToDiscordBot(currentMessageContent)
        sendMessageToTwitchChatAndLogIt(chat, "Message sent in #${channel.name} ${TwitchBotConfig.confirmEmote}")

        addedUserCoolDown = TwitchBotConfig.defaultUserCoolDown
        addedCommandCoolDown = TwitchBotConfig.defaultCommandCoolDown
    }
)