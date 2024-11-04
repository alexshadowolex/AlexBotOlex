package commands.twitchOnly

import handler.Command
import isCommandDisabled
import sendCommandDisabledMessage
import sendMessageToTwitchChatAndLogIt
import ui.SwitchStateVariables
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

private val booleanToCoinResult = mapOf(
    true to "heads",
    false to "tails"
)

val coinFlipCommand: Command = Command(
    names = listOf("coinflip", "cf", "flip"),
    description = "Flip a coin. Possible results: ${booleanToCoinResult.values.first()}, ${booleanToCoinResult.values.last()}",
    handler = {
        if(isCommandDisabled(SwitchStateVariables.isCoinFlipEnabled.value, messageEvent.user.name)) {
            sendCommandDisabledMessage("Coin Flip command", chat)
            return@Command
        }

        val flipResult = Random.nextBoolean()
        val message = "Coin Flip Result: ${booleanToCoinResult[flipResult]}"

        sendMessageToTwitchChatAndLogIt(
            chat,
            message
        )

        addedCommandCoolDown = 5.seconds
        addedUserCoolDown = 20.seconds
    }
)