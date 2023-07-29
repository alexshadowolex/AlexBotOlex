package commands.twitchOnly

import config.TwitchBotConfig
import handler.Command
import handler.EMPTY_MEME_AND_USER
import logger
import sendMessageToTwitchChatAndLogIt

val popMemeCommand: Command = Command(
    names = listOf("popmeme", "pm"),
    description = "Command only meant for the streamer. Pops the next meme out of the list.",
    handler = {
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

        sendMessageToTwitchChatAndLogIt(
            chat,
            message
        )
    }
)