import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential
import com.github.twitch4j.TwitchClientBuilder
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent
import com.github.twitch4j.common.enums.CommandPermission
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import javax.swing.JOptionPane
import kotlin.system.exitProcess

@Composable
@Preview
fun App() {
    var text by remember { mutableStateOf("Hello, World!") }

    MaterialTheme {
        Button(onClick = {
            text = "Hello, Desktop!"
        }) {
            Text(text)
        }
    }
}

fun main() = try {
    application {
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            setupTwitchBot()
        }

        Window(onCloseRequest = ::exitApplication) {
            App()
        }
    }
} catch (e: Throwable) {
    JOptionPane.showMessageDialog(null, e.message + "\n" + StringWriter().also { e.printStackTrace(PrintWriter(it)) }, "InfoBox: File Debugger", JOptionPane.INFORMATION_MESSAGE);
    exitProcess(0)
}

private fun setupTwitchBot() {
    val chatAccountToken = File("data/bot.token").readText()

    val twitchClient = TwitchClientBuilder.builder()
        .withEnableHelix(true)
        .withEnableChat(true)
        .withChatAccount(OAuth2Credential("twitch", chatAccountToken))
        .build()

    val lastCommandUsagePerUser = mutableMapOf<String, Instant>()

    twitchClient.chat.run {
        connect()
        joinChannel(BotConfig.channel)
        sendMessage(BotConfig.channel, "Bot running.")
    }

    twitchClient.eventManager.onEvent(ChannelMessageEvent::class.java) { messageEvent ->
        if (!messageEvent.message.startsWith("#")) {
            return@onEvent
        }

        val parts = messageEvent.message.trimStart('#').split(" ")
        val command = commands.find { it.name == parts.first() } ?: return@onEvent

        if (BotConfig.onlyMods && CommandPermission.MODERATOR in messageEvent.permissions) {
            twitchClient.chat.sendMessage(BotConfig.channel, "You do not have the required permissions to use this command.")
            return@onEvent
        }

        val lastCommandUsedInstant = lastCommandUsagePerUser.getOrPut(messageEvent.user.name) { Instant.now().minusSeconds(BotConfig.userCooldownSeconds) }

        if (Instant.now().isBefore(lastCommandUsedInstant.plusSeconds(BotConfig.userCooldownSeconds)) && CommandPermission.MODERATOR !in messageEvent.permissions) {
            val secondsUntilTimeoutOver = java.time.Duration.between(Instant.now(), lastCommandUsedInstant.plusSeconds(BotConfig.userCooldownSeconds)).seconds
            twitchClient.chat.sendMessage(BotConfig.channel, "You are still on cooldown. Please try again in $secondsUntilTimeoutOver seconds.")
            return@onEvent
        }

        val commandHandlerScope = CommandHandlerScope(
            chat = twitchClient.chat
        )

        command.handler(commandHandlerScope, parts.drop(1))

        if (commandHandlerScope.putUserOnCooldown) {
            lastCommandUsagePerUser[messageEvent.user.name] = Instant.now()
        }
    }
}