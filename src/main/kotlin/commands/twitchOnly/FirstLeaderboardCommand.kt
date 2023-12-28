package commands.twitchOnly

import config.TwitchBotConfig
import handler.Command
import isCommandDisabled
import pluralForm
import sendCommandDisabledMessage
import sendMessageToTwitchChatAndLogIt
import ui.SwitchStateVariables

val firstLeaderboardCommand: Command = Command(
    names = listOf("fl", "firstleaderboard"),
    description = "Displays the top 3 leaderboard of twitch chatters that got " +
            "${TwitchBotConfig.commandPrefix}${firstCommand.names.first()}. " +
            "If an in the leaderboard existing username is given as argument, " +
            "the place of that user and their amount will be displayed instead.",
    handler = { arguments ->
        if(isCommandDisabled(SwitchStateVariables.isFirstLeaderboardEnabled.value, messageEvent.user.name)) {
            sendCommandDisabledMessage("First leaderboard command", chat)
            return@Command
        }

        val userNameToSearchFor = if (arguments.isNotEmpty()) {
            arguments.firstOrNull { it.isNotEmpty() }
        } else {
            null
        }
        val message = if (userNameToSearchFor != null) {
            val entry = firstLeaderboardHandler.getLeaderboardEntry(userNameToSearchFor)
            if (entry != null) {
                val place = entry.first
                val firstLeaderBoardEntry = entry.second
                "${firstLeaderBoardEntry.userName} is on place $place with ${firstLeaderBoardEntry.amount} " +
                        "time".pluralForm(firstLeaderBoardEntry.amount) +
                        " getting first." +
                        if(place > 1) {
                            " Git gud!"
                        } else {
                            ""
                        }
            } else {
                "User name $userNameToSearchFor does not exist in leaderboard. Maybe they should be first for once ${TwitchBotConfig.shrugEmote}"
            }
        } else {
            val top3Leaderboard = firstLeaderboardHandler.getTop3Leaderboard()
            if(top3Leaderboard.isNotEmpty()) {
                "Top 3 first leaderboard entries: " + top3Leaderboard.joinToString(separator = " ") { entry ->
                    "Place " + (top3Leaderboard.indexOf(entry) + 1) + ": " + entry.userName + ", amount: " + entry.amount + " | "
                }
            } else {
                "Yo no one ever claimed first, wtf?!"
            }
        }

        sendMessageToTwitchChatAndLogIt(
            chat,
            message
        )

        addedCommandCoolDown = TwitchBotConfig.defaultCommandCoolDown
    }
)