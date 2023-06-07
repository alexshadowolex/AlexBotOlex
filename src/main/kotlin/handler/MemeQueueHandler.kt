package handler

import json
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import logger
import java.io.File

val EMPTY_MEME_AND_USER = MemeAndUser("", "")

class MemeQueueHandler {
    private var memeQueueSaveFile = File("data\\memeQueue.json")

    private var memeQueue = listOf<MemeAndUser>()
        private set(value) {
            field = value
            memeQueueSaveFile.writeText(json.encodeToString(field))
        }

    init {
        memeQueue = if (!memeQueueSaveFile.exists()) {
            memeQueueSaveFile.createNewFile()
            logger.info("Meme Queue file created.")
            mutableListOf()
        } else {
            try {
                json.decodeFromString<List<MemeAndUser>>(memeQueueSaveFile.readText()).toMutableList().also { currentMemeQueueData ->
                    logger.info("Existing meme queue file found! Values: ${currentMemeQueueData.joinToString(" | ")}")
                }
            } catch (e: Exception) {
                logger.warn("Error while reading meme queue file. Did something alter its content? Manually check the content below, fix it, put it in and restart the app!", e)
                try {
                    logger.warn("\n" + memeQueueSaveFile.readText())
                } catch (e: Exception) {
                    logger.error("Something went wrong with reading the meme queue file content yet again. Aborting...")
                    throw ExceptionInInitializerError()
                }
                logger.info("Initializing empty list for meme queue!")
                mutableListOf()
            }
        }
    }


    fun popNextMeme(): MemeAndUser {
        return if(memeQueue.isNotEmpty()) {
            memeQueue.first().also {
                memeQueue = memeQueue.drop(1).toMutableList()
                logger.info("Popped new meme: $it")
            }
        } else {
            EMPTY_MEME_AND_USER
        }.also {
            logger.info("New meme queue list: ${memeQueue.joinToString("|")}")
        }
    }

    fun addMeme(meme: String, user: String) {
        memeQueue = (memeQueue + MemeAndUser(meme, user)).also {
            logger.info("Added meme text $meme to the list by user $user!")
            logger.info("New meme queue list: ${it.joinToString("|")}")
        }
    }
}

@Serializable
data class MemeAndUser (
    val meme: String,
    val user: String
)
