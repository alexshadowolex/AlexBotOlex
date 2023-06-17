
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.adamratzman.spotify.SpotifyClientApi
import com.adamratzman.spotify.models.Token
import com.adamratzman.spotify.models.Track
import com.adamratzman.spotify.spotifyClientApi
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential
import com.github.twitch4j.TwitchClient
import com.github.twitch4j.TwitchClientBuilder
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent
import com.github.twitch4j.common.enums.CommandPermission
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.store.FileDataStoreFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ValueRange
import commands.twitchOnly.soundAlertPlayerJob
import config.GoogleSpreadSheetConfig
import config.SpotifyConfig
import config.TwitchBotConfig
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.supplier.EntitySupplyStrategy
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import handler.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.datetime.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.apache.commons.lang.time.DurationFormatUtils
import org.slf4j.LoggerFactory
import ui.App
import ui.clipOverlayPage
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.time.format.DateTimeFormatterBuilder
import java.util.*
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
                twitchClient.chat.sendMessage(TwitchBotConfig.channel, "Bot shutting down peepoLeave")
                soundAlertPlayerJob.cancel()
                logger.info("App shutting down...")
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
    JOptionPane.showMessageDialog(null, e.message + "\n" + StringWriter().also { e.printStackTrace(PrintWriter(it)) }, "InfoBox: File Debugger", JOptionPane.INFORMATION_MESSAGE)
    logger.error("Error while executing program.", e)
    exitProcess(0)
}

private fun setupTwitchBot(discordClient: Kord): TwitchClient {

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
        sendMessage(TwitchBotConfig.channel, "Bot running peepoArrive")
    }

    twitchClient.eventManager.onEvent(ChannelMessageEvent::class.java) { messageEvent ->
        val message = messageEvent.message
        if (!message.startsWith(TwitchBotConfig.commandPrefix)) {
            return@onEvent
        }

        val parts = message.substringAfter(TwitchBotConfig.commandPrefix).split(" ")
        // This feature has been built because of a_slowbro requesting too many screamy songs. The bot will not allow commands from blacklisted users
        if(messageEvent.user.name in TwitchBotConfig.blacklistedUsers || messageEvent.user.id in TwitchBotConfig.blacklistedUsers){
            twitchClient.chat.sendMessage(
                TwitchBotConfig.channel,
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

            twitchClient.chat.sendMessage(TwitchBotConfig.channel, "Command is still on cooldown. Please try again in $secondsUntilTimeoutOver")
            logger.info("Unable to execute command due to ongoing command cooldown.")

            return@onEvent
        }

        if ((Clock.System.now() - nextAllowedCommandUsageInstantForUser).isNegative() && messageEvent.user.name != TwitchBotConfig.channel) {
            val secondsUntilTimeoutOver = (nextAllowedCommandUsageInstantForUser - Clock.System.now()).inWholeSeconds.seconds

            twitchClient.chat.sendMessage(TwitchBotConfig.channel, "You are still on cooldown. Please try again in $secondsUntilTimeoutOver")
            logger.info("Unable to execute command due to ongoing cooldown.")

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
            nextAllowedCommandUsageInstantPerUser[key] = Clock.System.now() + commandHandlerScope.addedUserCooldown

            nextAllowedCommandUsageInstantPerCommand[command] = Clock.System.now() + commandHandlerScope.addedCommandCooldown
        }
    }

    logger.info("Twitch client started.")
    return twitchClient
}

suspend fun getCurrentSpotifySong(): Track? {
    return try {
        spotifyClient.player.getCurrentlyPlaying()?.item as Track
    } catch (_: Exception) {
        null
    }
}

fun createSongString(song: Track): String {
    return "\"${song.name}\"" +
            " by " +
            song.artists.map { it.name }.let { artists ->
                listOf(
                    artists.dropLast(1).joinToString(),
                    artists.last()
                ).filter { it.isNotBlank() }.joinToString(" and ")
            }
}

private const val CURRENT_SONG_FILE_NAME = "currentSong.txt"
fun startSpotifySongNameGetter() {
    CoroutineScope(Dispatchers.IO).launch {
        val currentSongFile = File("data\\displayFiles\\$CURRENT_SONG_FILE_NAME")
        var currentFileContent = if(currentSongFile.exists()) {
            currentSongFile.readText()
        } else {
            withContext(Dispatchers.IO) {
                currentSongFile.createNewFile()
            }
            ""
        }
        while(isActive) {
            delay(0.5.seconds)
            val currentTrack = getCurrentSpotifySong() ?: continue

            val currentSongString = createSongString(currentTrack)

            if(currentFileContent == currentSongString) {
                continue
            }

            currentFileContent = currentSongString
            currentSongFile.writeText(currentFileContent + " ".repeat(10))
        }
    }
}

private fun hostServer() {
    embeddedServer(io.ktor.server.cio.CIO, port = ClipPlayerConfig.port) {
        install(WebSockets)
        install(PartialContent)
        install(AutoHeadResponse)

        routing {
            clipOverlayPage()

            webSocket("/socket") {
                val clipPlayerInstance = ClipPlayerHandler.instance ?: run {
                    close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Clip player not setup."))
                    logger.error("Clip player not setup.")
                    return@webSocket
                }

                logger.info("Got new connection.")

                try {
                    for (frame in incoming) {
                        clipPlayerInstance.popNextRandomClip().let {
                            send(it)
                            logger.debug("Received video request from '${call.request.origin.remoteHost}', sending video '$it'.")
                        }
                    }
                } finally {
                    logger.info("User disconnected.")
                }
            }

            static("/video") {
                files(ClipPlayerConfig.clipLocation)
            }
        }
    }.start(wait = false)
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

private const val GOOGLE_CREDENTIALS_FILE_PATH = "data\\tokens\\google_credentials.json"
private const val STORED_CREDENTIALS_TOKEN_FOLDER = "data\\tokens"

private fun transformLetterToIndex(input: String): String {
    val output: String
    val columnsNames = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    output = (if(input.filter { it.isLetter() } != "") {
        columnsNames.indexOf(input)
    } else {
        columnsNames[input.toInt()]
    }).toString()

    return output
}

private fun setupSheetService(): Sheets? {
    var sheetService: Sheets? = null
    for(i in 0..1) {
        sheetService = try {
            val jsonFactory = GsonFactory.getDefaultInstance()
            val clientSecrets = GoogleClientSecrets.load(jsonFactory, File(GOOGLE_CREDENTIALS_FILE_PATH).reader())
            val httpTransport = GoogleNetHttpTransport.newTrustedTransport()

            val flow: GoogleAuthorizationCodeFlow = GoogleAuthorizationCodeFlow.Builder(
                httpTransport, jsonFactory, clientSecrets, Collections.singletonList(SheetsScopes.SPREADSHEETS)
            )
                .setDataStoreFactory(FileDataStoreFactory(File(STORED_CREDENTIALS_TOKEN_FOLDER)))
                .setAccessType("offline")
                .build()

            val receiver = LocalServerReceiver.Builder().setPort(8888).build()

            Sheets.Builder(httpTransport, jsonFactory, AuthorizationCodeInstalledApp(flow, receiver).authorize("user"))
                .setApplicationName("Sheet Service")
                .build()

        } catch (e: Exception) {
            logger.error("An error occured while setting up connection to google: ", e)
            null
        }

        if (sheetService == null) {
            logger.error("sheetService is null. Aborting...")
            return null
        }

        try {
            // dummy test to see if the credentials need to be refreshed
            sheetService.spreadsheets().values()
                .get(GoogleSpreadSheetConfig.soundAlertSpreadSheetId, "A1:A1")
                .execute()
        } catch (e: Exception) {
            logger.warn("Check for sheetService failed. Deleting token and trying again...")
            File("$STORED_CREDENTIALS_TOKEN_FOLDER\\StoredCredential").delete()
            continue
        }

        break
    }


    return sheetService
}

private fun getLocalSoundAlerts(): List<List<String>>? {

    return try {
        val soundAlertDirectory = File(TwitchBotConfig.soundAlertDirectory)
        val existingFiles = soundAlertDirectory.listFiles()!!.filter { it.extension in TwitchBotConfig.allowedSoundFiles } as MutableList
        val lineLength = transformLetterToIndex(GoogleSpreadSheetConfig.soundAlertLastDataCell.filter { it.isLetter() }).toInt() - transformLetterToIndex(GoogleSpreadSheetConfig.soundAlertFirstDataCell.filter { it.isLetter() }).toInt() + 1
        val output = mutableListOf<List<String>>()

        var i = 0
        while(true) {
            val currentStartIndex = lineLength * i
            if(existingFiles.subList(currentStartIndex, existingFiles.size).size > lineLength) {
                output.add(existingFiles.subList(currentStartIndex, currentStartIndex + lineLength).map { it.nameWithoutExtension })
            } else {
                output.add(existingFiles.subList(currentStartIndex, existingFiles.size).map { it.nameWithoutExtension }.let {
                    var output2 = it
                    for (j in 0 until lineLength - it.size) {
                        output2 = output2 + ""
                    }
                    output2
                })
                break
            }
            i++
        }

        for (j in output.size..GoogleSpreadSheetConfig.soundAlertLastDataCell.filter { it.isDigit() }.toInt() - GoogleSpreadSheetConfig.soundAlertFirstDataCell.filter { it.isDigit() }.toInt()) {
            output.add(listOf<String>().let {
                var output2 = it
                for (k in 0 until lineLength) {
                    output2 = output2 + ""
                }
                output2
            })
        }
        output
    } catch (e: Exception) {
        logger.error("An error occurred while reading local files ", e)
        null
    }
}

private fun getExistingCommands(): List<List<String>> {
    val output = mutableListOf<List<String>>()
    for(command in commands) {
        output.add(listOf(
            command.names.joinToString("|") { "${TwitchBotConfig.commandPrefix}${it}" },
            command.description
        ))
    }

    output.sortBy { it[0] }

    return output
}

private val spreadSheetVariables = object {
    val spreadSheetId = listOf(
        GoogleSpreadSheetConfig.soundAlertSpreadSheetId,
        GoogleSpreadSheetConfig.commandListSpreadSheetId
    )
    val sheetName = listOf(
        GoogleSpreadSheetConfig.soundAlertSheetName,
        GoogleSpreadSheetConfig.commandListSheetName
    )
    val tableRangeCells = listOf(
        "!${GoogleSpreadSheetConfig.soundAlertFirstDataCell}:${GoogleSpreadSheetConfig.soundAlertLastDataCell}",
        "!${GoogleSpreadSheetConfig.commandListFirstDataCell}:${GoogleSpreadSheetConfig.commandListLastDataCell}"
    )
    val lastUpdatedCell = listOf(
        GoogleSpreadSheetConfig.soundAlertLastUpdatedCell,
        GoogleSpreadSheetConfig.commandListLastUpdatedCell
    )
}

private enum class SpreadSheetType {
    SoundAlert, CommandList
}

private fun updateSpreadSheetList(sheetService: Sheets, localValues: List<List<String>>, spreadSheetType: SpreadSheetType) {
    val spreadSheetTypeOrdinal = spreadSheetType.ordinal

    @Suppress("UNCHECKED_CAST")
    val body: ValueRange = ValueRange()
        .setValues(localValues as List<MutableList<Any>>?)

    val tableRange = "'${spreadSheetVariables.sheetName[spreadSheetTypeOrdinal]}'${spreadSheetVariables.tableRangeCells[spreadSheetTypeOrdinal]}"

    try {
        sheetService.spreadsheets().values().update(spreadSheetVariables.spreadSheetId[spreadSheetTypeOrdinal], tableRange, body)
            .setValueInputOption("RAW")
            .execute()

        sheetService.spreadsheets().values().update(
            spreadSheetVariables.spreadSheetId[spreadSheetTypeOrdinal],
            spreadSheetVariables.sheetName[spreadSheetTypeOrdinal] + "!" + spreadSheetVariables.lastUpdatedCell[spreadSheetTypeOrdinal],
            ValueRange().setValues(
                listOf(
                    listOf(
                        Clock.System.now().toLocalDateTime(timeZone = kotlinx.datetime.TimeZone.currentSystemDefault())
                            .toString()
                    )
                )
            )
        )
            .setValueInputOption("RAW")
            .execute()
    } catch (e: Exception) {
        logger.error("Updating Spread Sheet ${spreadSheetType.name} failed ", e)
    }
}

private fun checkAndUpdateSpreadSheets() {
    val sheetService = setupSheetService()

    if (sheetService == null) {
        logger.error("sheetService is null. Aborting...")
        return
    }
    logger.info("Connected to google spread sheet service")

    val localSoundAlerts = getLocalSoundAlerts()
    if(localSoundAlerts == null) {
        logger.error("localSoundAlerts is null. Aborting the spread sheet update")
    } else {
        logger.info("Created list of local sound alerts")

        updateSpreadSheetList(sheetService, localSoundAlerts, SpreadSheetType.SoundAlert)

        logger.info("Updated sound alert spread sheet")
    }

    val existingCommands = getExistingCommands()
    if(existingCommands.isEmpty()) {
        logger.error("existingCommands is empty. Aborting the spread sheet update")
    } else {
        logger.info("Created list of existing commands")

        updateSpreadSheetList(sheetService, existingCommands, SpreadSheetType.CommandList)

        logger.info("Updated command list spread sheet")
    }

    logger.info("Finished updating spread sheets")
}

private const val TIMER_FILE_NAME = "data\\displayFiles\\timer.txt"
fun startTimer() {
    val timerFile = File(TIMER_FILE_NAME)
    if(!timerFile.exists()) {
        timerFile.createNewFile()
    }
    val endingTime = Clock.System.now() + TwitchBotConfig.timerDurationMinutes
    backgroundCoroutineScope.launch {
        while (isActive) {
            val timeLeft = endingTime - Clock.System.now()
            if(timeLeft.isNegative()) {
                break
            }
            val timeLeftFormatted = DurationFormatUtils.formatDuration(timeLeft.inWholeMilliseconds, "mm:ss", true)
            timerFile.writeText(timeLeftFormatted)
            delay(1.seconds)
        }
    }
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