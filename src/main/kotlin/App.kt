import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

var text = "Bot Running"

@Composable
@Preview
fun App() {

    MaterialTheme {
        Row(
            modifier = Modifier
                .padding(bottom = 24.dp)
        ) {
            Text(
                style = MaterialTheme.typography.body1,
                text = text
            )
        }
    }
}