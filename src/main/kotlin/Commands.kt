import com.github.twitch4j.chat.TwitchChat

data class Command(
    val name: String,
    val handler: CommandHandlerScope.(arguments: List<String>) -> Unit
)

data class CommandHandlerScope(
    val chat: TwitchChat,
    var putUserOnCooldown: Boolean = false
)

val commands = selfReferencing<List<Command>> {
    listOf(
        Command(
            name = "help",
            handler = {
                chat.sendMessage(BotConfig.channel, "Available commands: ${this@selfReferencing().joinToString(", ") { "#${it.name}" }}.")
            }
        ),
    )
}