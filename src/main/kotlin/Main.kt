
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.adamratzman.spotify.SpotifyClientApi
import com.adamratzman.spotify.spotifyClientApi
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential
import com.github.twitch4j.TwitchClient
import com.github.twitch4j.TwitchClientBuilder
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent
import com.github.twitch4j.common.enums.CommandPermission
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.features.json.*
import io.ktor.client.features.json.serializer.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import javax.swing.JOptionPane
import kotlin.system.exitProcess

fun main() = try {
    application {
        DisposableEffect(Unit) {
            val twitchClient = setupTwitchBot()

            onDispose {
                twitchClient.chat.sendMessage(BotConfig.channel, "Bot shutting down peepoLeave")
            }
        }

        LaunchedEffect(Unit) {
            spotifyClient = spotifyClientApi(
                clientId = BotConfig.spotifyClientId,
                clientSecret = BotConfig.spotifyClientSecret,
                redirectUri = "https://www.example.com",
                token = Json.decodeFromString(File("data/spotifytoken.json").readText())
            ).build()
        }

        Window(
            state = WindowState(size = DpSize(400.dp, 200.dp)),
            title = "AlexBotOlex",
            onCloseRequest = ::exitApplication
        ) {
            App()
        }
    }
} catch (e: Throwable) {
    JOptionPane.showMessageDialog(null, e.message + "\n" + StringWriter().also { e.printStackTrace(PrintWriter(it)) }, "InfoBox: File Debugger", JOptionPane.INFORMATION_MESSAGE);
    exitProcess(0)
}

lateinit var spotifyClient: SpotifyClientApi

val httpClient = HttpClient(CIO) {
    install(Logging)

    install(JsonFeature) {
        serializer = KotlinxSerializer(Json)
    }

    defaultRequest {
        header("Authorization", "Bearer ${spotifyClient.token.accessToken}")
    }
}

val commandHandlerCoroutineScope = CoroutineScope(Dispatchers.IO)

private fun setupTwitchBot(): TwitchClient {
    val chatAccountToken = File("data/twitchtoken.txt").readText()

    val twitchClient = TwitchClientBuilder.builder()
        .withEnableHelix(true)
        .withEnableChat(true)
        .withChatAccount(OAuth2Credential("twitch", chatAccountToken))
        .build()

    val lastCommandUsagePerUser = mutableMapOf<String, Instant>()

    twitchClient.chat.run {
        connect()
        joinChannel(BotConfig.channel)
        sendMessage(BotConfig.channel, "Bot running peepoArrive")
    }

    twitchClient.eventManager.onEvent(ChannelMessageEvent::class.java) { messageEvent ->
        if (!messageEvent.message.startsWith(BotConfig.commandPrefix)) {
            return@onEvent
        }

        val parts = messageEvent.message.substringAfter(BotConfig.commandPrefix).split(" ")
        val command = commands.find { parts.first() in it.names } ?: return@onEvent

        if (BotConfig.onlyMods && CommandPermission.MODERATOR in messageEvent.permissions) {
            twitchClient.chat.sendMessage(
                BotConfig.channel,
                "You do not have the required permissions to use this command."
            )
            return@onEvent
        }

        val lastCommandUsedInstant = lastCommandUsagePerUser.getOrPut(messageEvent.user.name) {
            Instant.now().minusSeconds(BotConfig.userCooldownSeconds)
        }

        if (Instant.now()
                .isBefore(lastCommandUsedInstant.plusSeconds(BotConfig.userCooldownSeconds)) && CommandPermission.MODERATOR !in messageEvent.permissions
        ) {
            val secondsUntilTimeoutOver = java.time.Duration.between(
                Instant.now(),
                lastCommandUsedInstant.plusSeconds(BotConfig.userCooldownSeconds)
            ).seconds
            twitchClient.chat.sendMessage(
                BotConfig.channel,
                "You are still on cooldown. Please try again in $secondsUntilTimeoutOver seconds."
            )
            return@onEvent
        }

        val commandHandlerScope = CommandHandlerScope(
            chat = twitchClient.chat,
            user = messageEvent.user
        )

        commandHandlerCoroutineScope.launch {
            command.handler(commandHandlerScope, parts.drop(1))

            if (commandHandlerScope.putUserOnCooldown) {
                lastCommandUsagePerUser[messageEvent.user.name] = Instant.now()
            }
        }
    }

    return twitchClient
}