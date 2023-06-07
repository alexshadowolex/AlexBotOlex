package handler

import json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import logger
import java.io.File

class ChannelPointsHandler {
    private var channelPointsSaveFile = File("data\\channelPointsPerUser.json")

    private var pointsPerUser = mutableMapOf</* user ID: */String, /* points: */Int>()
        private set(value) {
            field = value
            channelPointsSaveFile.writeText(json.encodeToString(field))
        }

    init {
        pointsPerUser = if (!channelPointsSaveFile.exists()) {
            channelPointsSaveFile.createNewFile()
            logger.info("Channel Points file created.")
            mutableMapOf()
        } else {
            try {
                json.decodeFromString<Map<String, Int>>(channelPointsSaveFile.readText()).toMutableMap().also { currentPointsPerUser ->
                    logger.info("Existing channel points file found! Values: ${currentPointsPerUser.values.joinToString(" | ")}")
                }
            } catch (e: Exception) {
                logger.warn("Error while reading channel points file. Did something alter its content? Manually check the content below, fix it, put it in and restart the app!", e)
                try {
                    logger.warn("\n" + channelPointsSaveFile.readText())
                } catch (e: Exception) {
                    logger.error("Something went wrong with reading the file content of channel points yet again. Aborting...")
                    throw ExceptionInInitializerError()
                }
                logger.info("Initializing empty map for channel points!")
                mutableMapOf()
            }
        }
    }
}