package dev.flsrg.bot.repo

import dev.flsrg.bot.db.HistMessage
import org.jetbrains.exposed.sql.Database

abstract class ChatHistRepository(val database: Database) {
    abstract fun getHist(userId: Long): List<HistMessage>
    abstract fun addMessage(userId: Long, message: HistMessage)
    abstract fun clearHistory(userId: Long)
    abstract fun removeLast(userId: Long)
    abstract fun removeFirst(userId: Long)
}