import java.io.File
import java.util.*

object ClipPlayerConfig {
    private val properties = Properties().apply {
        load(File("data\\properties\\clipPlayerConfig.properties").inputStream())
    }

    val clipLocation: String = properties.getProperty("clip_location")
    val allowedVideoFiles: List<String> = properties.getProperty("allowed_video_files").split(",")
    val port = properties.getProperty("port").toInt()
}