package commands.discordCommunictation

import DiscordBotConfig
import DiscordMessageContent
import config.TwitchBotConfig
import config.TwitchBotConfig.explanationEmote
import handler.Command
import sendMessageToDiscordBot
import sendMessageToTwitchChatAndLogIt
import ui.isSendClipEnabled
import kotlin.time.Duration.Companion.seconds

val sendClipCommand: Command = Command(
    names = listOf("sc", "sendclip", "clip", "clips"),
    description = "Automatically posts the given link of a clip in the clip channel on Discord. Anything aside from the link will be dropped.",
    handler = { arguments ->
        if(!isSendClipEnabled && TwitchBotConfig.channel != messageEvent.user.name) {
            sendMessageToTwitchChatAndLogIt(chat, "Send Clip is disabled ${TwitchBotConfig.commandDisabledEmote1} Now suck my ${TwitchBotConfig.commandDisabledEmote2}")
            return@Command
        }

        var link = arguments.filter { it.contains("https:") }.findLast { argument ->
            TwitchBotConfig.allowedDomains.any {
                argument.substringAfter("://").startsWith(it)
            }
        } ?: run {
            sendMessageToTwitchChatAndLogIt(
                chat,
                "No link has been provided ${TwitchBotConfig.rejectEmote} " +
                "Following link types are allowed: " +
                TwitchBotConfig.allowedDomains.map { "'${it}'" }.let { links ->
                    listOf(links.dropLast(1).joinToString(), links.last()).filter { it.isNotBlank() }.joinToString(" and ")
                } + ". Make sure, that your link starts with \"https://\" $explanationEmote"
            )
            addedUserCoolDown = 5.seconds
            return@Command
        }

        link = link.substring(link.indexOf("https:"))

        val currentMessageContent = DiscordMessageContent(
            message = DiscordMessageContent.Message.FromLink(link),
            title = "Clip for ",
            user = messageEvent.user.name,
            channelId = DiscordBotConfig.clipChannelId
        )

        val channel = sendMessageToDiscordBot(currentMessageContent)
        sendMessageToTwitchChatAndLogIt(chat, "Message sent in #${channel.name} ${TwitchBotConfig.confirmEmote}")

        addedUserCoolDown = TwitchBotConfig.defaultUserCoolDown
        addedCommandCoolDown = TwitchBotConfig.defaultCommandCoolDown
    }
)