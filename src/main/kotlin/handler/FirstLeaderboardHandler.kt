package handler

import json
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import logger
import java.io.File

class FirstLeaderboardHandler {
    private var firstLeaderboardSaveFile = File("data\\saveData\\firstLeaderboard.json")

    private var firstLeaderboard = listOf<FirstLeaderboardEntry>()
        private set(value) {
            field = value
            firstLeaderboardSaveFile.writeText(json.encodeToString(field))
        }

    init {
        firstLeaderboard = if (!firstLeaderboardSaveFile.exists()) {
            firstLeaderboardSaveFile.createNewFile()
            logger.info("First leaderboard file created.")
            mutableListOf()
        } else {
            try {
                val readList = json.decodeFromString<List<FirstLeaderboardEntry>>(firstLeaderboardSaveFile.readText()).toMutableList().also { currentFirstLeaderboard ->
                    logger.info("Existing first leaderboard file found! Values: ${currentFirstLeaderboard.joinToString(" | ")}")
                }
                val sortedList = readList.sortedByDescending { it.amount }
                sortedList
            } catch (e: Exception) {
                logger.warn("Error while reading first leaderboard file. Did something alter its content? Manually check the content below, fix it, put it in and restart the app!", e)
                try {
                    logger.warn("\n" + firstLeaderboardSaveFile.readText())
                } catch (e: Exception) {
                    logger.error("Something went wrong with reading the first leaderboard file content yet again. Aborting...")
                    throw ExceptionInInitializerError()
                }
                logger.info("Initializing empty list for first leaderboard!")
                mutableListOf()
            }
        }
    }


    fun getTop3Leaderboard(): List<FirstLeaderboardEntry> {
        return try {
            firstLeaderboard.subList(0, 3)
        } catch (e: IndexOutOfBoundsException) {
            firstLeaderboard.subList(0, firstLeaderboard.size)
        }
    }

    fun getLeaderboardEntry(userName: String): Pair<Int, FirstLeaderboardEntry>? {
        val index = firstLeaderboard.map { it.userName }.indexOf(userName)
        if(index == -1) {
            return null
        }

        return Pair(index + 1, firstLeaderboard[index])
    }

    fun addEntry(userName: String) {
        val tempLeaderboard = if(firstLeaderboard.map { it.userName }.contains(userName)) {
            val newAmount = firstLeaderboard[firstLeaderboard.map { it.userName }.indexOf(userName)].amount++
            logger.info("Increased the amount of first leaderboard entry for user $userName to $newAmount")
            firstLeaderboard
        } else {
            logger.info("Added new first leaderboard entry for user $userName")
            firstLeaderboard + FirstLeaderboardEntry(userName, 1)
        }

        firstLeaderboard = (tempLeaderboard.sortedByDescending { it.amount }).also {
            logger.info("New first leaderboard list: ${it.joinToString("|")}")
        }
    }
}

@Serializable
data class FirstLeaderboardEntry (
    val userName: String,
    var amount: Int
)