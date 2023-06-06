package ui

import ClipPlayerConfig
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
                    id = "overlay-title"

                    style = """
                        position: absolute;
                        display: flex;
                        height: 20%;
                        width: 20%;
                        font-size: 2vw;
                        left: 0;
                        top: 15vw;
                        border-radius: 0 50px 50px 0px;
                        padding: 0vw 4vw 1vw 2vw;
                        max-width: 50%;
                        color: white;
                        background-color: rgba(0, 0, 204, 0.3);
                        transition-delay: 0s;
                        font-weight: 700;
                        font-family: 'Trebuchet MS';
                        transform: translateX(0) skewY(-4deg);
                        align-items: center;
                        word-wrap: break-word;
                        z-index: 5;
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
                        z-index: 10;
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