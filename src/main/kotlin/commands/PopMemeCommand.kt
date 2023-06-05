package commands

import Command
import EMPTY_MEME_AND_USER
import config.TwitchBotConfig
import logger

val popMemeCommand: Command = Command(
    names = listOf("popmeme", "pm"),
    description = "Command only meant for the streamer. Pops the next meme out of the list.",
    handler = {
        logger.info("Called popMemeCommand")
        if(messageEvent.user.name != TwitchBotConfig.channel) {
            logger.info("User is not privileged to use command")
            return@Command
        }

        val nextMeme = memeQueueHandler.popNextMeme()
        val message = if(nextMeme == EMPTY_MEME_AND_USER) {
            "No memes in queue. Chat! You need to add some! ${TwitchBotConfig.worryStickEmote}"
        } else {
            "Next meme by ${nextMeme.user} is: ${nextMeme.meme}"
        }

        chat.sendMessage(
            TwitchBotConfig.channel,
            message
        )
    }
)