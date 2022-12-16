import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.kord.core.Kord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


val darkColorPalette = darkColors(
    primary = Color(0xff2244bb),
    onPrimary = Color.White,
    secondary = Color(0xff5bbbfe),
    background = Color.DarkGray,
    onBackground = Color.White,
)

@Composable
@Preview
fun App(discordClient: Kord) {
    var messageForDiscord by remember { mutableStateOf("") }

    MaterialTheme(colors = darkColorPalette) {
        Scaffold {
            Row (
                modifier = Modifier.padding(all = 10.dp)
            ) {
                Column (
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
                Column (
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
            Row {

            }
        }
    }
}