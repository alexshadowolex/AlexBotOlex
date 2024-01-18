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

object SwitchStateVariables {
    lateinit var isSongRequestEnabled: MutableState<Boolean>
    lateinit var isSoundAlertEnabled: MutableState<Boolean>
    lateinit var isTtsEnabled: MutableState<Boolean>
    lateinit var isMemeQueueEnabled: MutableState<Boolean>
    lateinit var isSongCommandEnabled: MutableState<Boolean>
    lateinit var isSongLouderEnabled: MutableState<Boolean>
    lateinit var isSpotifyQueueEnabled: MutableState<Boolean>
    lateinit var isVoteSkipEnabled: MutableState<Boolean>
    lateinit var isFeedbackEnabled: MutableState<Boolean>
    lateinit var isSendClipEnabled: MutableState<Boolean>
    lateinit var isFirstEnabled: MutableState<Boolean>
    lateinit var isFirstLeaderboardEnabled: MutableState<Boolean>
}

lateinit var messageForDiscord: MutableState<String>

@Composable
@Preview
fun app(discordClient: Kord) {
    messageForDiscord = remember { mutableStateOf("") }

    SwitchStateVariables.isSongRequestEnabled = remember { mutableStateOf(TwitchBotConfig.isSongRequestEnabledByDefault) }
    SwitchStateVariables.isSoundAlertEnabled = remember { mutableStateOf(TwitchBotConfig.isSoundAlertEnabledByDefault) }
    SwitchStateVariables.isTtsEnabled = remember { mutableStateOf(TwitchBotConfig.isTtsEnabledByDefault) }
    SwitchStateVariables.isMemeQueueEnabled = remember { mutableStateOf(TwitchBotConfig.isMemeQueueEnabledByDefault) }
    SwitchStateVariables.isSongCommandEnabled = remember { mutableStateOf(TwitchBotConfig.isSongCommandEnabledByDefault) }
    SwitchStateVariables.isSongLouderEnabled = remember { mutableStateOf(TwitchBotConfig.isSongLouderEnabledByDefault) }
    SwitchStateVariables.isSpotifyQueueEnabled = remember { mutableStateOf(TwitchBotConfig.isSpotifyQueueEnabledByDefault) }
    SwitchStateVariables.isVoteSkipEnabled = remember { mutableStateOf(TwitchBotConfig.isVoteSkipEnabledByDefault) }
    SwitchStateVariables.isFeedbackEnabled = remember { mutableStateOf(TwitchBotConfig.isFeedbackEnabledByDefault) }
    SwitchStateVariables.isSendClipEnabled = remember { mutableStateOf(TwitchBotConfig.isSendClipEnabledByDefault) }
    SwitchStateVariables.isFirstEnabled = remember { mutableStateOf(TwitchBotConfig.isFirstEnabledByDefault) }
    SwitchStateVariables.isFirstLeaderboardEnabled = remember { mutableStateOf(TwitchBotConfig.isFirstLeaderboardEnabled) }

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
                            value = messageForDiscord.value,
                            onValueChange = { value ->
                                messageForDiscord.value = value
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
                                    sendAnnouncementMessage(messageForDiscord.value, discordClient)
                                    messageForDiscord.value = ""
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
                                    checked = SwitchStateVariables.isSongRequestEnabled.value,
                                    onCheckedChange = {
                                        SwitchStateVariables.isSongRequestEnabled.value = it
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
                                    checked = SwitchStateVariables.isSoundAlertEnabled.value,
                                    onCheckedChange = {
                                        SwitchStateVariables.isSoundAlertEnabled.value = it
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
                                    checked = SwitchStateVariables.isTtsEnabled.value,
                                    onCheckedChange = {
                                        SwitchStateVariables.isTtsEnabled.value = it
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
                                    checked = SwitchStateVariables.isSongCommandEnabled.value,
                                    onCheckedChange = {
                                        SwitchStateVariables.isSongCommandEnabled.value = it
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
                                    checked = SwitchStateVariables.isSongLouderEnabled.value,
                                    onCheckedChange = {
                                        SwitchStateVariables.isSongLouderEnabled.value = it
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
                                    checked = SwitchStateVariables.isSpotifyQueueEnabled.value,
                                    onCheckedChange = {
                                        SwitchStateVariables.isSpotifyQueueEnabled.value = it
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
                                    checked = SwitchStateVariables.isVoteSkipEnabled.value,
                                    onCheckedChange = {
                                        SwitchStateVariables.isVoteSkipEnabled.value = it
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
                                    checked = SwitchStateVariables.isMemeQueueEnabled.value,
                                    onCheckedChange = {
                                        SwitchStateVariables.isMemeQueueEnabled.value = it
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
                                    checked = SwitchStateVariables.isFeedbackEnabled.value,
                                    onCheckedChange = {
                                        SwitchStateVariables.isFeedbackEnabled.value = it
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
                                    checked = SwitchStateVariables.isSendClipEnabled.value,
                                    onCheckedChange = {
                                        SwitchStateVariables.isSendClipEnabled.value = it
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
                                    text = "First Enabled",
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                                Switch(
                                    checked = SwitchStateVariables.isFirstEnabled.value,
                                    onCheckedChange = {
                                        SwitchStateVariables.isFirstEnabled.value = it
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
                                    .fillMaxWidth(0.25f)
                                    .padding(end = 1.dp)
                            ) {
                                Text(
                                    text = "First LB Enabled",
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                )
                                Switch(
                                    checked = SwitchStateVariables.isFirstLeaderboardEnabled.value,
                                    onCheckedChange = {
                                        SwitchStateVariables.isFirstLeaderboardEnabled.value = it
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