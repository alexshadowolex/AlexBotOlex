package handler
import com.github.twitch4j.chat.TwitchChat
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent
import commands.discordCommunictation.feedbackCommand
import commands.discordCommunictation.sendClipCommand
import commands.spotify.songCommand
import commands.spotify.songRequestCommand
import commands.spotify.spotifyQueueCommand
import commands.spotify.voteSkipCommand
import commands.twitchOnly.*
import dev.kord.core.Kord
import kotlin.time.Duration

data class Command(
    val names: List<String>,
    val handler: suspend CommandHandlerScope.(arguments: List<String>) -> Unit,
    val description: String
)

data class CommandHandlerScope(
    val discordClient: Kord,
    val chat: TwitchChat,
    val messageEvent: ChannelMessageEvent,
    val userIsPrivileged: Boolean,
    val memeQueueHandler: MemeQueueHandler,
    var addedUserCooldown: Duration = Duration.ZERO,
    var addedCommandCooldown: Duration = Duration.ZERO
)

val commands = listOf(
    helpCommand,
    songRequestCommand,
    textToSpeechCommand,
    soundAlertCommand,
    sendClipCommand,
    feedbackCommand,
    songCommand,
    spotifyQueueCommand,
    memeQueueCommand,
    popMemeCommand,
    raidMessageCommand,
    voteSkipCommand
)