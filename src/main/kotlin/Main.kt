
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.adamratzman.spotify.SpotifyClientApi
import com.adamratzman.spotify.models.Token
import com.adamratzman.spotify.spotifyClientApi
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential
import com.github.twitch4j.TwitchClient
import com.github.twitch4j.TwitchClientBuilder
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent
import com.github.twitch4j.common.enums.CommandPermission
import commands.twitchOnly.soundAlertPlayerJob
import config.SpotifyConfig
import config.TwitchBotConfig
import dev.kord.core.Kord
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import handler.Command
import handler.CommandHandlerScope
import handler.MemeQueueHandler
import handler.commands
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import ui.App
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import javax.swing.JOptionPane
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

val logger: org.slf4j.Logger = LoggerFactory.getLogger("Bot")

lateinit var spotifyClient: SpotifyClientApi

val httpClient = HttpClient(CIO) {
    install(Logging) {
        logger = Logger.DEFAULT
        level = LogLevel.INFO
    }

    install(ContentNegotiation) {
        json()
    }
}

val json = Json {
    prettyPrint = true
}

val backgroundCoroutineScope = CoroutineScope(Dispatchers.IO)

suspend fun main() = try {
    setupLogging()

    checkAndUpdateSpreadSheets()

    val discordClient = Kord(DiscordBotConfig.discordToken)
    logger.info("Discord client started.")

    CoroutineScope(discordClient.coroutineContext).launch {
        discordClient.login {
            @OptIn(PrivilegedIntent::class)
            intents += Intent.MessageContent
        }
    }
    val initialToken: Token = Json.decodeFromString(File("data\\tokens\\spotifyToken.json").readText())

    hostServer()

    application {
        DisposableEffect(Unit) {
            spotifyClient = runBlocking {
                spotifyClientApi(
                    clientId = SpotifyConfig.spotifyClientId,
                    clientSecret = SpotifyConfig.spotifyClientSecret,
                    redirectUri = "https://www.example.com",
                    token = initialToken
                ) {
                    onTokenRefresh = {
                        logger.info("Spotify Token refreshed")
                    }
                    afterTokenRefresh = {
                        it.token.refreshToken = initialToken.refreshToken
                        try {
                            File("data\\tokens\\spotifyToken.json").writeText(json.encodeToString(it.token.copy(refreshToken = initialToken.refreshToken)))
                        } catch(e: Exception) {
                            logger.error("Error occured while saving new token", e)
                        }
                    }
                    enableLogger = true
                }.build()
            }
            logger.info("Spotify client built successfully.")

            startSpotifySongNameGetter()

            val twitchClient = setupTwitchBot(discordClient)
            onDispose {
                sendMessageToTwitchChatAndLogIt(twitchClient.chat, "Bot shutting down peepoLeave")
                soundAlertPlayerJob.cancel()
                logger.info("App shutting down")
            }
        }

        Window(
            state = WindowState(size = DpSize(700.dp, 370.dp)),
            title = "AlexBotOlex",
            onCloseRequest = ::exitApplication,
            icon = painterResource("icon.ico"),
            resizable = false
        ) {
            App(discordClient)
        }
    }
} catch (e: Throwable) {
    JOptionPane.showMessageDialog(
        null,
        e.message + "\n" + StringWriter().also { e.printStackTrace(PrintWriter(it)) },
        "InfoBox: File Debugger",
        JOptionPane.INFORMATION_MESSAGE
    )
    logger.error("Error while executing program.", e)
    exitProcess(0)
}

fun setupTwitchBot(discordClient: Kord): TwitchClient {
    val twitchClient = TwitchClientBuilder.builder()
        .withEnableHelix(true)
        .withEnableChat(true)
        .withChatAccount(OAuth2Credential("twitch", TwitchBotConfig.chatAccountToken))
        .build()

    val nextAllowedCommandUsageInstantPerUser = mutableMapOf<Pair<Command, /* user: */ String>, Instant>()
    val nextAllowedCommandUsageInstantPerCommand = mutableMapOf<Command, Instant>()

    val memeQueueHandler = MemeQueueHandler()
    twitchClient.chat.run {
        connect()
        joinChannel(TwitchBotConfig.channel)
        sendMessageToTwitchChatAndLogIt(this, "Bot running peepoArrive")
    }

    twitchClient.eventManager.onEvent(ChannelMessageEvent::class.java) { messageEvent ->
        val message = messageEvent.message
        if (!message.startsWith(TwitchBotConfig.commandPrefix)) {
            return@onEvent
        }

        val parts = message.substringAfter(TwitchBotConfig.commandPrefix).split(" ")

        // This feature has been built because of SlowDivinity requesting too many screamy songs. The bot will not allow commands from blacklisted users
        if(messageEvent.user.name in TwitchBotConfig.blacklistedUsers || messageEvent.user.id in TwitchBotConfig.blacklistedUsers){
            sendMessageToTwitchChatAndLogIt(
                twitchClient.chat,
                "Imagine not being a blacklisted user. Couldn't be you ${messageEvent.user.name} ${TwitchBotConfig.blacklistEmote}"
            )
            if(messageEvent.user.id !in TwitchBotConfig.blacklistedUsers) {
                logger.warn("Blacklisted user ${messageEvent.user.name} tried using a command. Please use following ID in the properties file instead of the name: ${messageEvent.user.id}")
            }
            return@onEvent
        }

        val command = commands.find { parts.first().substringAfter(TwitchBotConfig.commandPrefix).lowercase() in it.names } ?: return@onEvent

        if (TwitchBotConfig.onlyMods && CommandPermission.MODERATOR !in messageEvent.permissions) {
            twitchClient.chat.sendMessage(
                TwitchBotConfig.channel,
                "You do not have the required permissions to use this command."
            )
            logger.info("User '${messageEvent.user.name}' does not have the necessary permissions to call command '${command.names.first()}'")

            return@onEvent
        }

        logger.info("User '${messageEvent.user.name}' tried using command '${command.names.first()}' with arguments: ${parts.drop(1).joinToString()}")

        val nextAllowedCommandUsageInstant = nextAllowedCommandUsageInstantPerCommand.getOrPut(command) {
            Clock.System.now()
        }

        val nextAllowedCommandUsageInstantForUser = nextAllowedCommandUsageInstantPerUser.getOrPut(command to messageEvent.user.name) {
            Clock.System.now()
        }
        if((Clock.System.now() - nextAllowedCommandUsageInstant).isNegative() && messageEvent.user.name != TwitchBotConfig.channel) {
            val secondsUntilTimeoutOver = (nextAllowedCommandUsageInstant - Clock.System.now()).inWholeSeconds.seconds

            sendMessageToTwitchChatAndLogIt(twitchClient.chat, "Command is still on cooldown. Please try again in $secondsUntilTimeoutOver")

            return@onEvent
        }

        if ((Clock.System.now() - nextAllowedCommandUsageInstantForUser).isNegative() && messageEvent.user.name != TwitchBotConfig.channel) {
            val secondsUntilTimeoutOver = (nextAllowedCommandUsageInstantForUser - Clock.System.now()).inWholeSeconds.seconds

            sendMessageToTwitchChatAndLogIt(twitchClient.chat, "You are still on cooldown. Please try again in $secondsUntilTimeoutOver")

            return@onEvent
        }

        val commandHandlerScope = CommandHandlerScope(
            discordClient = discordClient,
            chat = twitchClient.chat,
            messageEvent = messageEvent,
            memeQueueHandler = memeQueueHandler,
            userIsPrivileged = CommandPermission.MODERATOR in messageEvent.permissions
        )

        backgroundCoroutineScope.launch {
            command.handler(commandHandlerScope, parts.drop(1))

            val key = command to messageEvent.user.name
            nextAllowedCommandUsageInstantPerUser[key] = Clock.System.now() + commandHandlerScope.addedUserCoolDown

            nextAllowedCommandUsageInstantPerCommand[command] = Clock.System.now() + commandHandlerScope.addedCommandCoolDown
        }
    }

    logger.info("Twitch client started.")
    return twitchClient
}