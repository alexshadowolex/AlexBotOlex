
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
@Preview
fun App() {
    val coroutineScope = rememberCoroutineScope()
    MaterialTheme {
        Button(
            onClick = {
                coroutineScope.launch {
                    spotifyClientHandler.buildSpotifyClient()
                }
            }
        ){
            Text(
                text = "Refresh Spotify Client"
            )
        }
    }
}