package dev.flsrg.bot.hist

import dev.flsrg.bot.BotConfig
import dev.flsrg.bot.db.HistMessage
import dev.flsrg.bot.db.MessageHistTable
import dev.flsrg.bot.repo.ChatHistRepository
import dev.flsrg.bot.repo.UserRepository
import dev.flsrg.llmpollingclient.model.ChatMessage
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque

class HistoryManager(private val histRepository: ChatHistRepository, usersRepository: UserRepository) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val chatHistories = ConcurrentHashMap<Long, LinkedBlockingDeque<ChatMessage>>()

    init {
        // Prefetch existing histories from database on startup
        transaction(histRepository.database) {
            val userIdsToFetch = usersRepository.getActiveUsers(14).map { it.id }

            MessageHistTable.selectAll()
                .where { MessageHistTable.userId inList userIdsToFetch }
                .forEach { row ->
                    val chatId = row[MessageHistTable.userId]
                    val messages = Json.decodeFromString<List<ChatMessage>>(row[MessageHistTable.messages])
                    log.info("Fetched messages $messages")
                    chatHistories[chatId] = LinkedBlockingDeque(messages)
                }

            log.info("Fetched ${chatHistories.size} histories for ${userIdsToFetch.size} users")
        }
    }

    fun getHistory(userId: Long): List<ChatMessage> {
        return chatHistories[userId]?.toList()
            ?: histRepository.getHist(userId.toLong()).map { it.toChatMessage() }.also {
                chatHistories[userId] = LinkedBlockingDeque(it)
            }
    }

    fun addMessage(userId: Long, message: ChatMessage) {
        chatHistories
            .getOrPut(userId) { LinkedBlockingDeque() }
            .apply {
                addLast(message)
                while (size > BotConfig.MAX_HISTORY_SIZE) {
                    removeFirst()
                }
            }

        histRepository.addMessage(userId.toLong(), message.toHistMessage())
    }

    fun clearHistory(userId: Long) {
        chatHistories.remove(userId)
        histRepository.clearHistory(userId)
    }

    fun removeLast(userId: Long) {
        chatHistories[userId]?.removeLast()
        histRepository.removeLast(userId)
    }

    private fun HistMessage.toChatMessage() = ChatMessage(
        role = this.role,
        content = this.content,
    )

    private fun ChatMessage.toHistMessage() = HistMessage(
        role = this.role,
        content = this.content,
    )
}