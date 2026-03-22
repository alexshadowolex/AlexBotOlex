package config

import commands.twitchOnly.TtsMonsterVoice
import getPropertyValue
import json
import logger
import java.io.File
import java.io.FileOutputStream
import java.util.*

object CacheConfig {
    private val cacheConfigFile = File("data\\properties\\cacheConfig.properties")
    private val properties = Properties().apply {
        if(!cacheConfigFile.exists()) {
            logger.warn(
                "Error while reading property file ${cacheConfigFile.path} in CacheConfig init: " +
                        "File does not exist!"
            )

            cacheConfigFile.createNewFile()
            logger.info("Created new file ${cacheConfigFile.path}")
        } else
            load(cacheConfigFile.inputStream())
    }


    var ttsVoices: List<TtsMonsterVoice>? = json.decodeFromString(getPropertyValue(
        properties,
        propertyName = "ttsVoices",
        propertiesFileRelativePath = cacheConfigFile.path,
        setPropertyIfNotExisting = true
    ))
        set(value) {
            field = value
            properties.setProperty("ttsVoices", json.encodeToString(value))
            savePropertiesToFile()
        }


    private fun savePropertiesToFile() {
        try {
            properties.store(FileOutputStream(cacheConfigFile.path), null)
        } catch (e: Exception) {
            logger.error("Error while saving properties to file in BotConfig.")
            logger.error(e.stackTraceToString())
        }
    }
}