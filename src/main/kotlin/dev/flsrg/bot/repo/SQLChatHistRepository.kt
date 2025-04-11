package dev.flsrg.bot.repo

import dev.flsrg.bot.db.HistMessage
import dev.flsrg.bot.db.MessageHistTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class SQLChatHistRepository(database: Database): ChatHistRepository(database) {
    override fun getHist(userId: Long): List<HistMessage> {
        return MessageHistTable.selectAll()
            .where { MessageHistTable.userId eq userId }
            .orderBy(MessageHistTable.timestamp to SortOrder.ASC)
            .map {
                HistMessage(
                    role = it[MessageHistTable.role],
                    content = it[MessageHistTable.content],
                    timestamp = it[MessageHistTable.timestamp]
                )
            }
    }

    override fun addMessage(userId: Long, message: HistMessage) {
        transaction(database) {
            MessageHistTable.insert {
                it[this.userId] = userId
                it[role] = message.role
                it[content] = message.content
                it[timestamp] = message.timestamp
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

            MessageHistTable.upsert {
                currentMessages
            }
        }
    }

    override fun removeFirst(userId: Long) {
        val currentMessages = getHist(userId).toMutableList()
        if (currentMessages.isNotEmpty()) {
            currentMessages.removeFirst()

            MessageHistTable.upsert {
                currentMessages
            }
        }
    }
}