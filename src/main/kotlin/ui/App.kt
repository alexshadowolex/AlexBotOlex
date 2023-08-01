package ui
import ClipPlayerConfig
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import backgroundCoroutineScope
import config.TwitchBotConfig
import dev.kord.core.Kord
import handler.ClipPlayerHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import resetSpotifyVolumeToDefault
import sendAnnouncementMessage
import startTimer
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI


val darkColorPalette = darkColors(
    primary = Color(0xff2244bb),
    onPrimary = Color.White,
    secondary = Color(0xff5bbbfe),
    background = Color.DarkGray,
    onBackground = Color.White,
)

var isSongRequestEnabled = TwitchBotConfig.isSongRequestEnabledByDefault
var isSoundAlertEnabled = TwitchBotConfig.isSoundAlertEnabledByDefault
var isTtsEnabled = TwitchBotConfig.isTtsEnabledByDefault
var isMemeQueueEnabled = TwitchBotConfig.isMemeQueueEnabledByDefault
var isSongCommandEnabled = TwitchBotConfig.isSongCommandEnabledByDefault
var isSongLouderEnabled = TwitchBotConfig.isSongLouderEnabledByDefault
var isSpotifyQueueEnabled = TwitchBotConfig.isSpotifyQueueEnabledByDefault
var isVoteSkipEnabled = TwitchBotConfig.isVoteSkipEnabledByDefault
var isFeedbackEnabled = TwitchBotConfig.isFeedbackEnabledByDefault
var isSendClipEnabled = TwitchBotConfig.isSendClipEnabledByDefault

@Composable
@Preview
fun app(discordClient: Kord) {
    var messageForDiscord by remember { mutableStateOf("") }
    val isSongRequestChecked = remember { mutableStateOf(TwitchBotConfig.isSongRequestEnabledByDefault) }
    val isSoundAlertChecked = remember { mutableStateOf(TwitchBotConfig.isSoundAlertEnabledByDefault) }
    val isTtsChecked = remember { mutableStateOf(TwitchBotConfig.isTtsEnabledByDefault) }
    val isMemeQueueChecked = remember { mutableStateOf(TwitchBotConfig.isMemeQueueEnabledByDefault) }
    val isSongCommandChecked = remember { mutableStateOf(TwitchBotConfig.isSongCommandEnabledByDefault) }
    val isSongLouderChecked = remember { mutableStateOf(TwitchBotConfig.isSongLouderEnabledByDefault) }
    val isSpotifyQueueChecked = remember { mutableStateOf(TwitchBotConfig.isSpotifyQueueEnabledByDefault) }
    val isVoteSkipChecked = remember { mutableStateOf(TwitchBotConfig.isVoteSkipEnabledByDefault) }
    val isFeedbackChecked = remember { mutableStateOf(TwitchBotConfig.isFeedbackEnabledByDefault) }
    val isSendClipChecked = remember { mutableStateOf(TwitchBotConfig.isSendClipEnabledByDefault) }

    MaterialTheme(colors = darkColorPalette) {
        Scaffold {
            Column {
                Row(
                    modifier = Modifier.padding(all = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(0.7F)
                    ) {
                        TextField(
                            label = {
                                Text(
                                    color = MaterialTheme.colors.onPrimary,
                                    text = "Message For Discord"
                                )
                            },
                            value = messageForDiscord,
                            onValueChange = { value ->
                                messageForDiscord = value
                            },
                            singleLine = true,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .fillMaxWidth()
                        )
                    }
                    Column(
                        modifier = Modifier.weight(0.3F)
                    ) {
                        Button(
                            onClick = {
                                CoroutineScope(Dispatchers.IO).launch {
                                    sendAnnouncementMessage(messageForDiscord, discordClient)
                                    messageForDiscord = ""
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            Text("Send Message On Discord")
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxSize()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(bottom = 24.dp)
                        ) {
                            Text(
                                style = MaterialTheme.typography.body1,
                                text = "Clips hosted on "
                            )

                            Text(
                                style = MaterialTheme.typography.body1,
                                text = "http://localhost:${ClipPlayerConfig.port}",
                                modifier = Modifier
                                    .clickable {
                                        backgroundCoroutineScope.launch {
                                            Toolkit.getDefaultToolkit().systemClipboard.setContents(
                                                StringSelection("http://localhost:${ClipPlayerConfig.port}"),
                                                null
                                            )

                                            withContext(Dispatchers.IO) {
                                                Desktop.getDesktop()
                                                    .browse(URI.create("http://localhost:${ClipPlayerConfig.port}"))
                                            }
                                        }
                                    }
                                    .padding(3.dp)
                                    .pointerHoverIcon(PointerIcon.Hand),
                                textDecoration = TextDecoration.Underline,
                                color = MaterialTheme.colors.secondary
                            )
                        }

                        Row(
                            modifier = Modifier
                                .padding(top = 4.dp)
                        ) {
                            Text(
                                text = "Currently Playing: ${ClipPlayerHandler.instance?.currentlyPlayingClip?.collectAsState()?.value ?: "Nothing"}"
                            )
                        }

                        Row(
                            modifier = Modifier
                                .padding(top = 20.dp)
                        ) {
                            Column (
                                modifier = Modifier
                                    .weight(0.25f)
                                    .padding(end = 1.dp)
                            ) {
                                Text(
                                    text = "Song Request Enabled",
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                                Switch(
                                    checked = isSongRequestChecked.value,
                                    onCheckedChange = {
                                        isSongRequestChecked.value = it
                                        isSongRequestEnabled = it
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                            }

                            Column (
                                modifier = Modifier
                                    .weight(0.25f)
                                    .padding(end = 1.dp)
                            ) {
                                Text(
                                    text = "Sound Alert Enabled",
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                                Switch(
                                    checked = isSoundAlertChecked.value,
                                    onCheckedChange = {
                                        isSoundAlertChecked.value = it
                                        isSoundAlertEnabled = it
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                            }

                            Column (
                                modifier = Modifier
                                    .weight(0.25f)
                                    .padding(end = 1.dp)
                            ) {
                                Text(
                                    text = "TTS Enabled",
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                                Switch(
                                    checked = isTtsChecked.value,
                                    onCheckedChange = {
                                        isTtsChecked.value = it
                                        isTtsEnabled = it
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                            }

                            Column (
                                modifier = Modifier
                                    .weight(0.25f)
                                    .padding(start = 0.dp)
                            ) {
                                Text(
                                    text = "Song Comm Enabled",
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                                Switch(
                                    checked = isSongCommandChecked.value,
                                    onCheckedChange = {
                                        isSongCommandChecked.value = it
                                        isSongRequestEnabled = it
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .padding(top = 20.dp)
                        ) {
                            Column (
                                modifier = Modifier
                                    .weight(0.25f)
                                    .padding(end = 1.dp)
                            ) {
                                Text(
                                    text = "Song Louder Enabled",
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                                Switch(
                                    checked = isSongLouderChecked.value,
                                    onCheckedChange = {
                                        isSongLouderChecked.value = it
                                        isSongLouderEnabled = it
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                            }

                            Column (
                                modifier = Modifier
                                    .weight(0.25f)
                                    .padding(end = 1.dp)
                            ) {
                                Text(
                                    text = "Spotify Queue Enabled",
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                                Switch(
                                    checked = isSpotifyQueueChecked.value,
                                    onCheckedChange = {
                                        isSpotifyQueueChecked.value = it
                                        isSpotifyQueueEnabled = it
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                            }

                            Column (
                                modifier = Modifier
                                    .weight(0.25f)
                                    .padding(end = 1.dp)
                            ) {
                                Text(
                                    text = "Vote Skip Enabled",
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                                Switch(
                                    checked = isVoteSkipChecked.value,
                                    onCheckedChange = {
                                        isVoteSkipChecked.value = it
                                        isVoteSkipEnabled = it
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                            }

                            Column (
                                modifier = Modifier
                                    .weight(0.25f)
                                    .padding(start = 0.dp)
                            ) {
                                Text(
                                    text = "Meme Queue Enabled",
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                                Switch(
                                    checked = isMemeQueueChecked.value,
                                    onCheckedChange = {
                                        isMemeQueueChecked.value = it
                                        isMemeQueueEnabled = it
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                            }
                        }


                        Row(
                            modifier = Modifier
                                .padding(top = 20.dp)
                        ) {
                            Column (
                                modifier = Modifier
                                    .weight(0.25f)
                                    .padding(end = 1.dp)
                            ) {
                                Text(
                                    text = "Feedback Enabled",
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                                Switch(
                                    checked = isFeedbackChecked.value,
                                    onCheckedChange = {
                                        isFeedbackChecked.value = it
                                        isFeedbackEnabled = it
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                            }

                            Column (
                                modifier = Modifier
                                    .weight(0.25f)
                                    .padding(end = 1.dp)
                            ) {
                                Text(
                                    text = "Send Clip Enabled",
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                                Switch(
                                    checked = isSendClipChecked.value,
                                    onCheckedChange = {
                                        isSendClipChecked.value = it
                                        isSendClipEnabled = it
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .padding(top = 5.dp)
                        ) {
                            Column (
                                modifier = Modifier
                                    .weight(0.5f)
                                    .padding(end = 2.dp)
                            ) {
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        backgroundCoroutineScope.launch {
                                            resetSpotifyVolumeToDefault()
                                        }
                                    }
                                ) {
                                    Text(
                                        text = "Reset Spotify Volume"
                                    )
                                }
                            }
                            Column (
                                modifier = Modifier
                                    .weight(0.5f)
                                    .padding(start = 2.dp)
                            ) {
                                Button(
                                    onClick = {
                                        startTimer()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Start Timer"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}