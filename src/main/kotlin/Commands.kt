
import com.github.twitch4j.chat.TwitchChat
import com.github.twitch4j.common.events.domain.EventUser
import commands.helpCommand
import commands.songRequestCommand
import commands.soundAlertCommand
import commands.textToSpeechCommand
import kotlin.time.Duration

data class Command(
    val names: List<String>,
    val handler: suspend CommandHandlerScope.(arguments: List<String>) -> Unit,
    val description: String
)

data class CommandHandlerScope(
    val chat: TwitchChat,
    val user: EventUser,
    val userIsPrivileged: Boolean,
    var addedUserCooldown: Duration = Duration.ZERO,
)

val commands = listOf(
    helpCommand,
    songRequestCommand,
    textToSpeechCommand,
    soundAlertCommand
)