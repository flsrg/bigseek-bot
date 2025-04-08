package dev.flsrg.bot.repo

import dev.flsrg.bot.BotConfig
import dev.flsrg.bot.db.HistMessage
import dev.flsrg.bot.db.MessageHistTable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import org.slf4j.LoggerFactory

class SQLChatHistRepository(database: Database): ChatHistRepository(database) {
    override fun getHist(userId: Long): List<HistMessage> {
        return MessageHistTable.select(Expression.build { MessageHistTable.userId eq userId })
            .singleOrNull()
            ?.getOrNull(MessageHistTable.messages)
            ?.let { Json.decodeFromString<List<HistMessage>>(it) }
            ?: emptyList()
    }

    override fun addMessage(userId: Long, message: HistMessage) {
        transaction(database) {
            val currentMessages = getHist(userId).toMutableList()
            currentMessages.add(message)
            LoggerFactory.getLogger(javaClass).info("current $currentMessages")

            val trimmedMessages = if (currentMessages.size > BotConfig.MAX_HISTORY_SIZE) {
                currentMessages.drop(1)
            } else {
                currentMessages
            }


            val messagesJson = Json.encodeToString(trimmedMessages)
            LoggerFactory.getLogger(javaClass).info("Adding $message to user $userId \n encoded $messagesJson")

            MessageHistTable.upsert {
                it[MessageHistTable.userId] = userId
                it[messages] = messagesJson
            }
        }
    }

    override fun clearHistory(userId: Long) {
        transaction(database) {
            MessageHistTable.deleteWhere { SqlExpressionBuilder.run { MessageHistTable.userId eq userId } }
        }
    }

    override fun removeLast(userId: Long) {
        val currentMessages = getHist(userId).toMutableList()
        if (currentMessages.isNotEmpty()) {
            currentMessages.removeLast()
            val messagesJson = Json.encodeToString(currentMessages)

            MessageHistTable.upsert {
                it[MessageHistTable.userId] = userId
                it[messages] = messagesJson
            }
        }
    }

    override fun removeFirst(userId: Long) {
        val currentMessages = getHist(userId).toMutableList()
        if (currentMessages.isNotEmpty()) {
            currentMessages.removeFirst()
            val messagesJson = Json.encodeToString(currentMessages)

            MessageHistTable.upsert {
                it[MessageHistTable.userId] = userId
                it[messages] = messagesJson
            }
        }
    }
}