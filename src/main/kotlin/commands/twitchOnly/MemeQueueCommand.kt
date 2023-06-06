package commands.twitchOnly

import handler.Command
import config.TwitchBotConfig
import logger
import kotlin.time.Duration.Companion.minutes

val memeQueueCommand: Command = Command(
    names = listOf("memequeue", "mq"),
    description = "After \"each\" reset, we watch a meme to calm down. Link a possible meme (preferred a video, funny), maybe with some explanation (if needed).",
    handler = { arguments ->
        logger.info("Called memeQueueCommand with arguments ${arguments.joinToString(" ")}")
        val meme = arguments.joinToString(" ").trim()
        var coolDown = 5.minutes

        val message = if(meme.isEmpty()) {
            coolDown = TwitchBotConfig.defaultUserCooldown
            "Dude you did not give a meme, shame on you ${TwitchBotConfig.commandDisabledEmote1}"
        } else {
            memeQueueHandler.addMeme(
                meme = meme,
                user = messageEvent.user.name
            )

            "Meme was added to the list successfully ${TwitchBotConfig.confirmEmote}"
        }

        chat.sendMessage(
            TwitchBotConfig.channel,
            message
        )

        addedCommandCooldown = TwitchBotConfig.defaultCommandCooldown
        addedUserCooldown = coolDown
    }
)