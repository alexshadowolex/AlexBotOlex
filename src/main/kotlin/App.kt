import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.kord.core.Kord


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
    MaterialTheme(colors = darkColorPalette) {
        Scaffold {
            
        }
    }
}