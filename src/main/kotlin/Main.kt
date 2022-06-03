
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
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import javax.swing.JOptionPane
import kotlin.system.exitProcess

const val LOGDIRECTORY_NAME = "logs"
const val LOGFILE_NAME = "$LOGDIRECTORY_NAME/AlexBotOlex.log"
lateinit var out: PrintStream

fun main() = try {
    setupLogging()
    application {
        DisposableEffect(Unit) {
            val twitchClient = setupTwitchBot()

            onDispose {
                twitchClient.chat.sendMessage(BotConfig.channel, "Bot shutting down peepoLeave")
                debugLog("INFO", "App Ending")
                out.close()
            }
        }

        LaunchedEffect(Unit) {
            spotifyClient = spotifyClientApi(
                clientId = BotConfig.spotifyClientId,
                clientSecret = BotConfig.spotifyClientSecret,
                redirectUri = "https://www.example.com",
                token = Json.decodeFromString(File("data/spotifytoken.json").readText())
            ).build()
            debugLog("INFO", "Spotify Client built")
        }

        Window(
            state = WindowState(size = DpSize(400.dp, 200.dp)),
            title = "AlexBotOlex",
            onCloseRequest = ::exitApplication
        ) {
            App()
        }
        debugLog("INFO", "App Started")
    }
} catch (e: Throwable) {
    JOptionPane.showMessageDialog(null, e.message + "\n" + StringWriter().also { e.printStackTrace(PrintWriter(it)) }, "InfoBox: File Debugger", JOptionPane.INFORMATION_MESSAGE);
    debugLog("ERROR", e.message + "\n" + StringWriter().also { e.printStackTrace(PrintWriter(it)) })
    out.close()
    exitProcess(0)
}

lateinit var spotifyClient: SpotifyClientApi

val httpClient = HttpClient(CIO) {
    install(Logging){
        logger = Logger.DEFAULT
        level = LogLevel.ALL
    }

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

        debugLog("INFO", "Command called: ${command.names.joinToString() }} by ${messageEvent.user.name} with arguments: ${parts.drop(1).joinToString()}")

        if (BotConfig.onlyMods && CommandPermission.MODERATOR in messageEvent.permissions) {
            twitchClient.chat.sendMessage(
                BotConfig.channel,
                "You do not have the required permissions to use this command."
            )
            debugLog("INFO", "User ${messageEvent.user} has no permission to call $command")
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
            debugLog("INFO", "User ${messageEvent.user} is still on cooldown")
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

    debugLog("INFO", "Twitch Bot Started")
    return twitchClient
}

fun setupLogging(){
    val logFile = File(LOGFILE_NAME)
    val logDirectory = File(LOGDIRECTORY_NAME)
    if(!logDirectory.exists() || !logDirectory.isDirectory){
        Files.createDirectory(Paths.get(LOGDIRECTORY_NAME))
    }
    val fileNewlyCreated = !logFile.exists()
    if(fileNewlyCreated){
        logFile.createNewFile()
    }
    out = PrintStream(FileOutputStream(LOGFILE_NAME))
    if(fileNewlyCreated) {
        debugLog("INFO", "Log file ${logFile.name} has been created")
    }
    debugLog("INFO", "Logging has been set up")
}