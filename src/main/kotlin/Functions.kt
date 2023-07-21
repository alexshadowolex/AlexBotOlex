
import com.adamratzman.spotify.models.Track
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
import config.GoogleSpreadSheetConfig
import config.TwitchBotConfig
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.supplier.EntitySupplyStrategy
import handler.ClipPlayerHandler
import handler.CommandHandlerScope
import handler.commands
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toLocalDateTime
import org.apache.commons.lang.time.DurationFormatUtils
import ui.clipOverlayPage
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths
import java.time.format.DateTimeFormatterBuilder
import java.util.*
import kotlin.time.Duration.Companion.seconds

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
                "\nhttps://www.twitch.tv/alexshadowolex"
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