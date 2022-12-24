
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
import commands.soundAlertPlayerJob
import config.TwitchBotConfig
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
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
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.time.format.DateTimeFormatterBuilder
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

val commandHandlerCoroutineScope = CoroutineScope(Dispatchers.IO)

suspend fun main() = try {
    setupLogging()

    val discordToken = File("data/discordtoken.txt").readText()
    val discordClient = Kord(discordToken)

    logger.info("Discord client started.")

    CoroutineScope(discordClient.coroutineContext).launch {
        discordClient.login {
            @OptIn(PrivilegedIntent::class)
            intents += Intent.MessageContent
        }
    }
    val initialToken: Token = Json.decodeFromString(File("data/spotifytoken.json").readText())

    application {
        DisposableEffect(Unit) {
            spotifyClient = runBlocking {
                spotifyClientApi(
                    clientId = TwitchBotConfig.spotifyClientId,
                    clientSecret = TwitchBotConfig.spotifyClientSecret,
                    redirectUri = "https://www.example.com",
                    token = initialToken
                ) {
                    onTokenRefresh = {
                        logger.info("Token refreshed")
                    }
                    afterTokenRefresh = {
                        // logger.info("new token after refresh: ${it.token}")
                        // This line has not been tested yet, but I figure this is the way to do it
                        it.token.refreshToken = initialToken.refreshToken
                        try {
                            File("data/spotifytoken.json").writeText(json.encodeToString(it.token.copy(refreshToken = initialToken.refreshToken)))
                        } catch(e: Exception) {
                            logger.error("Error occured while saving new token", e)
                        }
                    }
                    enableLogger = true
                }.build()
            }

            logger.info("Spotify client built successfully.")

            val twitchClient = setupTwitchBot(discordClient)

            onDispose {
                twitchClient.chat.sendMessage(TwitchBotConfig.channel, "Bot shutting down peepoLeave")
                soundAlertPlayerJob.cancel()
                logger.info("App shutting down...")
            }
        }

        Window(
            state = WindowState(size = DpSize(700.dp, 250.dp)),
            title = "AlexBotOlex",
            onCloseRequest = ::exitApplication,
            icon = painterResource("icon.ico")
        ) {
            App(discordClient)
        }
    }
} catch (e: Throwable) {
    JOptionPane.showMessageDialog(null, e.message + "\n" + StringWriter().also { e.printStackTrace(PrintWriter(it)) }, "InfoBox: File Debugger", JOptionPane.INFORMATION_MESSAGE)
    logger.error("Error while executing program.", e)
    exitProcess(0)
}

private fun setupTwitchBot(discordClient: Kord): TwitchClient {
    val chatAccountToken = File("data/twitchtoken.txt").readText()

    val twitchClient = TwitchClientBuilder.builder()
        .withEnableHelix(true)
        .withEnableChat(true)
        .withChatAccount(OAuth2Credential("twitch", chatAccountToken))
        .build()

    val nextAllowedCommandUsageInstantPerUser = mutableMapOf<Pair<Command, /* user: */ String>, Instant>()

    twitchClient.chat.run {
        connect()
        joinChannel(TwitchBotConfig.channel)
        sendMessage(TwitchBotConfig.channel, "Bot running peepoArrive")
    }

    twitchClient.eventManager.onEvent(ChannelMessageEvent::class.java) { messageEvent ->
        val message = messageEvent.message
        if (!message.startsWith(TwitchBotConfig.commandPrefix)) {
            return@onEvent
        }

        val parts = message.substringAfter(TwitchBotConfig.commandPrefix).split(" ")
        val command = commands.find { parts.first().lowercase() in it.names } ?: return@onEvent

        if (TwitchBotConfig.onlyMods && CommandPermission.MODERATOR !in messageEvent.permissions) {
            twitchClient.chat.sendMessage(
                TwitchBotConfig.channel,
                "You do not have the required permissions to use this command."
            )
            logger.info("User '${messageEvent.user.name}' does not have the necessary permissions to call command '${command.names.first()}'")

            return@onEvent
        }

        logger.info("User '${messageEvent.user.name}' tried using command '${command.names.first()}' with arguments: ${parts.drop(1).joinToString()}")

        val nextAllowedCommandUsageInstant = nextAllowedCommandUsageInstantPerUser.getOrPut(command to messageEvent.user.name) {
            Clock.System.now()
        }

        if ((Clock.System.now() - nextAllowedCommandUsageInstant).isNegative() && CommandPermission.MODERATOR !in messageEvent.permissions) {
            val secondsUntilTimeoutOver = (nextAllowedCommandUsageInstant - Clock.System.now()).inWholeSeconds.seconds

            twitchClient.chat.sendMessage(TwitchBotConfig.channel, "You are still on cooldown. Please try again in $secondsUntilTimeoutOver")
            logger.info("Unable to execute command due to ongoing cooldown.")

            return@onEvent
        }

        val commandHandlerScope = CommandHandlerScope(
            discordClient = discordClient,
            chat = twitchClient.chat,
            messageEvent = messageEvent,
            userIsPrivileged = CommandPermission.MODERATOR in messageEvent.permissions
        )

        commandHandlerCoroutineScope.launch {
            command.handler(commandHandlerScope, parts.drop(1))

            val key = command to messageEvent.user.name
            nextAllowedCommandUsageInstantPerUser[key] = nextAllowedCommandUsageInstantPerUser[key]!! + commandHandlerScope.addedUserCooldown
        }
    }

    logger.info("Twitch client started.")
    return twitchClient
}

suspend fun CommandHandlerScope.sendMessageToDiscordBot(discordMessageContent: DiscordMessageContent): TextChannel {
    val user = discordMessageContent.user
    val messageTitle = discordMessageContent.title
    val message = discordMessageContent.message

    val channel = discordClient.getChannelOf<TextChannel>(discordMessageContent.channelId, EntitySupplyStrategy.cacheWithCachingRestFallback)
        ?: error("Invalid channel ID.")

    val channelName = channel.name
    val channelId = channel.id

    logger.info("User: $user | Title: $messageTitle | Message/Link: $message | Channel Name: $channelName | Channel ID: $channelId")

    channel.createEmbed {
        title = messageTitle + channelName
        author {
            name = "Twitch user $user"
        }
        description = when (message) {
            is DiscordMessageContent.Message.FromLink -> ""
            is DiscordMessageContent.Message.FromText -> message.text
        }
        color = DiscordBotConfig.embedAccentColor
    }

    if (message is DiscordMessageContent.Message.FromLink) {
        channel.createMessage(message.link)
    }

    logger.info("Embed/Message created on Discord Channel $channelName")

    return channel
}

suspend fun sendAnnouncementMessage(messageForDiscord: String, discordClient: Kord) {
    val channel = discordClient.getChannelOf<TextChannel>(DiscordBotConfig.announcementChannelId, EntitySupplyStrategy.cacheWithCachingRestFallback)
        ?: error("Invalid channel ID.")

    val channelName = channel.name
    val channelId = channel.id

    logger.info("Discord message: $messageForDiscord | Channel Name: $channelName | Channel ID: $channelId")

    channel.createMessage(
        "$messageForDiscord\n" +
                DiscordBotConfig.announcementUsers.joinToString(" ") { "<@$it>" } +
        "\nhttps://www.twitch.tv/alexshadowolex"
    )

    logger.info("Message created on Discord Channel $channelName")
}


private const val LOG_DIRECTORY = "logs"

fun setupLogging() {
    Files.createDirectories(Paths.get(LOG_DIRECTORY))

    val logFileName = DateTimeFormatterBuilder()
        .appendInstant(0)
        .toFormatter()
        .format(Clock.System.now().toJavaInstant())
        .replace(':', '-')

    val logFile = Paths.get(LOG_DIRECTORY, "${logFileName}.log").toFile().also {
        if (!it.exists()) {
            it.createNewFile()
        }
    }

    System.setOut(PrintStream(MultiOutputStream(System.out, FileOutputStream(logFile))))

    logger.info("Log file '${logFile.name}' has been created.")
}