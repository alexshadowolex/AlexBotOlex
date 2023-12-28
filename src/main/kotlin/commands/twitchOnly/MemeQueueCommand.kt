package commands.twitchOnly

import config.TwitchBotConfig
import handler.Command
import isCommandDisabled
import sendCommandDisabledMessage
import sendMessageToTwitchChatAndLogIt
import ui.SwitchStateVariables
import kotlin.time.Duration.Companion.minutes

val memeQueueCommand: Command = Command(
    names = listOf("memequeue", "mq"),
    description = "After \"each\" reset, we watch a meme to calm down. Link a possible meme (preferred a video, funny), maybe with some explanation (if needed).",
    handler = { arguments ->
        if(isCommandDisabled(SwitchStateVariables.isMemeQueueEnabled.value, messageEvent.user.name)) {
            sendCommandDisabledMessage("Meme queue command", chat)
            return@Command
        }

        val meme = arguments.joinToString(" ").trim()
        var coolDown = 5.minutes

        val message = if(meme.isEmpty()) {
            coolDown = TwitchBotConfig.defaultUserCoolDown
            "Dude you did not give a meme, shame on you ${TwitchBotConfig.commandDisabledEmote1}"
        } else {
            memeQueueHandler.addMeme(
                meme = meme,
                user = messageEvent.user.name
            )

            "Meme was added to the list successfully ${TwitchBotConfig.confirmEmote}"
        }

        sendMessageToTwitchChatAndLogIt(
            chat,
            message
        )

        addedCommandCoolDown = TwitchBotConfig.defaultCommandCoolDown
        addedUserCoolDown = coolDown
    }
)