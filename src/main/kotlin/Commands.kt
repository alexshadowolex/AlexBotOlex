
import com.github.twitch4j.chat.TwitchChat
import com.github.twitch4j.common.events.domain.EventUser
import commands.helpCommand
import commands.songRequestCommand
import commands.textToSpeechCommand

data class Command(
    val names: List<String>,
    val handler: suspend CommandHandlerScope.(arguments: List<String>) -> Unit
)

data class CommandHandlerScope(
    val chat: TwitchChat,
    val user: EventUser,
    var putUserOnCooldown: Boolean = false,
    var putCommandOnCooldown: Boolean = false,
)

val commands = listOf(
    helpCommand,
    songRequestCommand,
    textToSpeechCommand,
)