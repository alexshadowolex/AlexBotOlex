import com.github.twitch4j.chat.TwitchChat
import com.github.twitch4j.common.events.domain.EventUser

data class Command(
    val name: String,
    val handler: CommandHandlerScope.(arguments: List<String>) -> Unit
)

data class CommandHandlerScope(
    val chat: TwitchChat,
    val user: EventUser,
    var putUserOnCooldown: Boolean = false
)

val commands = selfReferencing<List<Command>> {
    listOf(
        Command(
            name = "help",
            handler = {
                chat.sendMessage(BotConfig.channel, "Available commands: ${this@selfReferencing().joinToString(", ") { "#${it.name}" }}.")
                text += "\nCommand \"${this@selfReferencing()[0].name}\" used by $user"
            }
        ),
    )
}