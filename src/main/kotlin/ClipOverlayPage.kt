import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.routing.*
import kotlinx.html.*

fun Route.clipOverlayPage() {
    get("/") {
        call.respondHtml {
            body {
                style = """
                    padding: 0;
                    margin: 0;
                """.trimIndent()

                style {
                    unsafe {
                        raw(
                            """
                                #video-player {
                                    position: absolute;
                                    height: 100%;
                                    width: 100%;
                                }
                            """.trimIndent()
                        )
                    }
                }

                video {
                    id = "video-player"
                    autoPlay = true

                    style = """
                        position: absolute;
                        height: 100%;
                        width: 100%;
                    """.trimIndent()
                }

                div {
                    id = "warning"

                    style = """
                        position: absolute;
                        display: flex;
                        width: 100%;
                        height: 100%;
                        color: red;
                        font-family: 'Arial';
                        font-size: 48px;
                        justify-content: center;
                        align-items: center;
                        z-index: 1;
                    """.trimIndent()

                    classes = setOf("hidden")

                    +"Disconnected. Please reload page."
                }

                script {
                    unsafe {
                        raw("""
                            const serverPort = '${ClipPlayerConfig.port}';
                        """.trimIndent())
                    }
                }

                script {
                    unsafe {
                        raw((object { })::class.java.getResource("ClipOverlayPageLogic.js")!!.readText())
                    }
                }
            }
        }
    }
}