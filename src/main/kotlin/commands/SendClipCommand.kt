package commands

import Command
import DiscordBotConfig
import DiscordMessageContent
import config.TwitchBotConfig
import config.TwitchBotConfig.explanationEmote
import logger
import sendMessageToDiscordBot
import kotlin.time.Duration.Companion.seconds

val sendClipCommand: Command = Command(
    names = listOf("sc", "sendclip", "clip", "clips"),
    description = "Automatically posts the given link of a clip in the clip channel on Discord. Anything aside from the link will be dropped.",
    handler = { arguments ->
        var link = arguments.filter { it.contains("https:") }.findLast { argument ->
            TwitchBotConfig.allowedDomains.any {
                argument.substringAfter("://").startsWith(it)
            }
        } ?: run {
            chat.sendMessage(
                TwitchBotConfig.channel,
                "No link has been provided ${TwitchBotConfig.rejectEmote} " +
                "Following link types are allowed: " +
                TwitchBotConfig.allowedDomains.map { "'${it}'" }.let { links ->
                    listOf(links.dropLast(1).joinToString(), links.last()).filter { it.isNotBlank() }.joinToString(" and ")
                } + ". Make sure, that your link starts with \"https://\" $explanationEmote"
            )
            addedUserCooldown = 5.seconds
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
        val messageSentOnTwitchChat = chat.sendMessage(TwitchBotConfig.channel, "Message sent in #${channel.name} ${TwitchBotConfig.confirmEmote}")
        logger.info("Message sent to Twitch Chat: $messageSentOnTwitchChat")

        addedUserCooldown = TwitchBotConfig.userCooldown
        addedCommandCooldown = TwitchBotConfig.commandCooldown
    }
)