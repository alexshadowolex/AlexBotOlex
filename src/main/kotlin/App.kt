
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIconDefaults
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.kord.core.Kord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
@Preview
fun App(discordClient: Kord) {
    var messageForDiscord by remember { mutableStateOf("") }

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
                                    .pointerHoverIcon(PointerIconDefaults.Hand),
                                textDecoration = TextDecoration.Underline,
                                color = MaterialTheme.colors.secondary
                            )
                        }

                        Row(
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .weight(1f)
                        ) {
                            Text(
                                text = "Currently Playing: ${ClipPlayer.instance?.currentlyPlayingClip?.collectAsState()?.value ?: "Nothing"}"
                            )
                        }

                        Row {
                            Button(
                                onClick = {
                                    ClipPlayer.instance?.resetPlaylistFile()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Reset Playlist"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}