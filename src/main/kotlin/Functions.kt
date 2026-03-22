
import com.adamratzman.spotify.models.Track
import com.github.twitch4j.TwitchClient
import com.github.twitch4j.chat.TwitchChat
import com.github.twitch4j.chat.events.channel.RaidEvent
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
import commands.twitchOnly.TtsMonsterVoice
import commands.twitchOnly.TtsMonsterVoicesResponse
import config.CacheConfig
import config.GoogleSpreadSheetConfig
import config.SpotifyConfig
import config.TwitchBotConfig
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.supplier.EntitySupplyStrategy
import handler.ClipPlayerHandler
import handler.CommandHandlerScope
import handler.commands
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.apache.commons.lang.time.DurationFormatUtils
import ui.SwitchStateVariables
import ui.clipOverlayPage
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths
import java.time.format.DateTimeFormatterBuilder
import java.util.*
import javax.swing.JOptionPane
import kotlin.system.exitProcess
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaInstant

// Twitch Functions
fun handleRaidEvent(raidEvent: RaidEvent, twitchClient: TwitchClient) {
    logger.info("Called handleRaidEvent")
    val raiderId = raidEvent.raider.id
    val raiderName = raidEvent.raider.name

    logger.info("Raider name: $raiderName | Raider ID: $raiderId")

    sendMessageToTwitchChatAndLogIt(raidEvent.twitchChat, "!so $raiderName")

    try {
        val response = twitchClient.helix.sendShoutout(
            TwitchBotConfig.chatAccountToken,
            TwitchBotConfig.channelAccountId,
            raiderId,
            TwitchBotConfig.chatAccountId
        )

        logger.error("response.executionException.stackTrace: ${response.executionException.stackTrace}")
        logger.error("response.executionException.cause: ${response.executionException.cause}")
        logger.error("response.executionException.message: ${response.executionException.message}")
        logger.error("response.executionTimeInMilliseconds: ${response.executionTimeInMilliseconds}")
        logger.error("response.failedExecutionException.stackTrace: ${response.failedExecutionException.stackTrace}")
        logger.error("response.failedExecutionException.cause: ${response.failedExecutionException.cause}")
        logger.error("response.failedExecutionException.message: ${response.failedExecutionException.message}")
        logger.error("response.isExecutedInThread: ${response.isExecutedInThread}")
        logger.error("response.isExecutionComplete: ${response.isExecutionComplete}")
        logger.error("response.isFailedExecution: ${response.isFailedExecution}")
        logger.error("response.isSuccessfulExecution: ${response.isSuccessfulExecution}")
        logger.error("response.isResponseRejected: ${response.isResponseRejected}")
        logger.error("response.isResponseTimedOut: ${response.isResponseTimedOut}")
        logger.error("response.metrics: ${response.metrics}")

        logger.info("Issued Shoutout successfully")
    } catch (e: Exception) {
        logger.error("Something went wrong when sending the Shoutout", e)
    }
}

fun isCommandDisabled(commandEnabled: Boolean, userName: String): Boolean {
    return !commandEnabled && userName != TwitchBotConfig.channel
}

fun sendCommandDisabledMessage(messagePrefix: String, chat: TwitchChat) {
    sendMessageToTwitchChatAndLogIt(
        chat = chat,
        message = "$messagePrefix is disabled ${TwitchBotConfig.commandDisabledEmote1} " +
            "Now suck my ${TwitchBotConfig.commandDisabledEmote2}")
}

fun startQuoteMessageGetter(chat: TwitchChat) {
    backgroundCoroutineScope.launch {
        while (isActive) {
            sendMessageToTwitchChatAndLogIt(chat, TwitchBotConfig.quoteMessage)
            delay(TwitchBotConfig.quoteMessageDelayMinutes)
        }
    }
}

// Spotify Functions
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
        var currentFileContent: String
        if(!currentSongFile.exists()) {
            withContext(Dispatchers.IO) {
                currentSongFile.createNewFile()
            }
        }

        while(isActive) {
            val currentTrack = getCurrentSpotifySong() ?: continue

            val currentSongString = createSongString(currentTrack)

            currentFileContent = currentSongString
            currentSongFile.writeText(currentFileContent + " ".repeat(10))
            delay(2.seconds)
        }
    }
}

suspend fun setSpotifyVolume(volume: Int) {
    spotifyClient.player.setVolume(volume)
}

suspend fun resetSpotifyVolumeToDefault() {
    setSpotifyVolume(SpotifyConfig.defaultVolume)
}

// Local Server hosting
fun hostServer() {
    embeddedServer(CIO, port = ClipPlayerConfig.port) {
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

                logger.info("Got new connection for Clip player.")

                try {
                    for (frame in incoming) {
                        send(clipPlayerInstance.popNextRandomClip())
                    }
                } finally {
                    logger.info("User disconnected.")
                }
            }

            staticFiles(
                "/video",
                File(ClipPlayerConfig.clipLocation)
            )
        }
    }.start(wait = false)
}

// Discord functions
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
                "\nhttps://www.twitch.tv/${TwitchBotConfig.channel}"
    )

    logger.info("Message created on Discord Channel $channelName")
}


// Spreadsheet handling
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
            logger.error("An error occurred while setting up connection to google: ", e)
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

private fun getSoundAlertsForSpreadSheet(): List<List<String>>? {

    return try {
        val soundAlertDirectory = File(TwitchBotConfig.soundAlertDirectory)
        val existingFiles = soundAlertDirectory.listFiles()!!.filter { it.extension in TwitchBotConfig.allowedSoundFiles } as MutableList
        val lineLength = transformLetterToIndex(GoogleSpreadSheetConfig.soundAlertLastDataCell.filter { it.isLetter() }).toInt() - transformLetterToIndex(
            GoogleSpreadSheetConfig.soundAlertFirstDataCell.filter { it.isLetter() }).toInt() + 1
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

private fun getExistingCommandsForSpreadSheet(): List<List<String>> {
    val output = mutableListOf<List<String>>()
    for(command in commands) {
        output.add(listOf(
            command.names.joinToString(" | ") { "${TwitchBotConfig.commandPrefix}${it}" },
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
                        Clock.System.now().toLocalDateTime(timeZone = TimeZone.currentSystemDefault())
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

fun checkAndUpdateSpreadSheets() {
    val sheetService = setupSheetService()

    if (sheetService == null) {
        logger.error("sheetService is null. Aborting...")
        return
    }
    logger.info("Connected to google spread sheet service")

    val localSoundAlerts = getSoundAlertsForSpreadSheet()
    if(localSoundAlerts == null) {
        logger.error("localSoundAlerts is null. Aborting the spread sheet update")
    } else {
        logger.info("Created list of local sound alerts")

        updateSpreadSheetList(sheetService, localSoundAlerts, SpreadSheetType.SoundAlert)

        logger.info("Updated sound alert spread sheet")
    }

    val existingCommands = getExistingCommandsForSpreadSheet()
    if(existingCommands.isEmpty()) {
        logger.error("existingCommands is empty. Aborting the spread sheet update")
    } else {
        logger.info("Created list of existing commands")

        updateSpreadSheetList(sheetService, existingCommands, SpreadSheetType.CommandList)

        logger.info("Updated command list spread sheet")
    }

    logger.info("Finished updating spread sheets")
}

// Timer
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


// Logging
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

fun sendMessageToTwitchChatAndLogIt(chat: TwitchChat, message: String) {

    val parts = mutableListOf<String>()
    var current = StringBuilder()

    for (word in message.split(" ")) {
        val next = if (current.isEmpty()) word else " $word"

        if (current.length + next.length > 500) {
            parts.add(current.toString())
            current = StringBuilder(word)
        } else {
            current.append(next)
        }
    }

    if (current.isNotEmpty()) {
        parts.add(current.toString())
    }

    parts.forEach {
        chat.sendMessage(TwitchBotConfig.channel, it)
        logger.info("Sent Twitch chat message: $it")
    }
}


// UI handling
fun setAllUiSwitches(value: Boolean) {
    SwitchStateVariables.isSongRequestEnabled.value = value
    SwitchStateVariables.isSoundAlertEnabled.value = value
    SwitchStateVariables.isTtsEnabled.value = value
    SwitchStateVariables.isMemeQueueEnabled.value = value
    SwitchStateVariables.isSongCommandEnabled.value = value
    SwitchStateVariables.isSongLouderEnabled.value = value
    SwitchStateVariables.isSpotifyQueueEnabled.value = value
    SwitchStateVariables.isVoteSkipEnabled.value = value
    SwitchStateVariables.isFeedbackEnabled.value = value
    SwitchStateVariables.isSendClipEnabled.value = value
    SwitchStateVariables.isFirstEnabled.value = value
    SwitchStateVariables.isFirstLeaderboardEnabled.value = value
}


/**
 * Displays an error message in a modal dialog window.
 *
 * @param message the content of the error message
 * @param title the title of the dialog window
 */
fun showErrorMessageWindow(message: String, title: String) {
    JOptionPane.showMessageDialog(
        null,
        "$message\nCheck logs for more information",
        title,
        JOptionPane.ERROR_MESSAGE
    )
}


// General Functions
/**
 * Reads a property value from the given [Properties] object.
 *
 * If the property cannot be read because it is not existing and the flag `setPropertyIfNotExisting`
 * is set to true, the property will be created with an empty string.
 * If not, displays an error dialog and terminates the application.
 *
 * @param properties the [Properties] instance to read from
 * @param propertyName the key of the property
 * @param propertiesFileRelativePath the relative path of the properties file (used in error messages)
 * @param setPropertyIfNotExisting if true, the property is created with an empty value when it does not exist;
 * otherwise, the application logs an error and terminates.
 * @return the raw value of the property as a [String]
 */
fun getPropertyValue(
    properties: Properties, propertyName: String,
    propertiesFileRelativePath: String,
    setPropertyIfNotExisting: Boolean
): String {
    return try {
        properties.getProperty(propertyName)
    } catch (e: Exception) {
        if(setPropertyIfNotExisting) {
            val emptyString = ""
            properties.setProperty(propertyName, emptyString)
            logger.info("Created property $propertyName in file $propertiesFileRelativePath with empty value.")
            emptyString
        } else {
            logger.error(
                "Exception occurred while reading property $propertyName in file $propertiesFileRelativePath: ",
                e
            )
            showErrorMessageWindow(
                message = "Error while reading value of property ${propertyName.addQuotationMarks()} " +
                        "in file $propertiesFileRelativePath.\n" +
                        "Try running the latest version of UpdateProperties.jar " +
                        "or fix it manually by adding it to the mentioned file.",
                title = "Error while reading properties"
            )
            logger.error("test")
            exitProcess(-1)
        }
    }
}

suspend fun getVoicesFromTtsMonsterApi(chat: TwitchChat): List<TtsMonsterVoice>? {
    var voices: List<TtsMonsterVoice>? = null
    try {
        val httpClient = HttpClient(io.ktor.client.engine.cio.CIO) {
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.NONE
            }

            install(ContentNegotiation) {
                json()
            }
        }

        val json = Json {
            ignoreUnknownKeys = true
        }

        val endpoint = "https://api.console.tts.monster/voices"
        val httpResponse = httpClient.post(endpoint) {
            header("Authorization", TwitchBotConfig.ttsMonsterToken)
            contentType(ContentType.Application.Json)
        }

        if (httpResponse.status != HttpStatusCode.OK) {
            sendMessageToTwitchChatAndLogIt(chat, "Something went wrong with getting the TTS voices.")
            logger.error("Error while getting TTS voices: ${httpResponse.bodyAsText()}")
            return null
        }

        voices = json.decodeFromString<TtsMonsterVoicesResponse>(httpResponse.bodyAsText()).voices
        CacheConfig.ttsVoices = voices
        logger.info("Refreshed voices cache successfully")
    } catch (e: Exception) {
        sendMessageToTwitchChatAndLogIt(chat, "Unable to get TTS voices.")
        logger.error("Unable to get TTS voices:", e)
    }

    return voices
}