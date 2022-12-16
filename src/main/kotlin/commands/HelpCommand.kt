package commands

import config.TwitchBotConfig
import Command
import commands

val helpCommand: Command = Command(
    names = listOf("help"),
    handler = {
        chat.sendMessage(TwitchBotConfig.channel, "Available commands: ${commands.joinToString("; ") { command -> command.names.joinToString("|") { "${TwitchBotConfig.commandPrefix}${it}" } }}.")
    }
)