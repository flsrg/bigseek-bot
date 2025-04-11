package dev.flsrg.bot.hist

import dev.flsrg.bot.BotConfig
import dev.flsrg.bot.db.HistMessage
import dev.flsrg.bot.repo.ChatHistRepository
import dev.flsrg.bot.repo.UserRepository
import dev.flsrg.llmpollingclient.model.ChatMessage
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
            val hist = userIdsToFetch.associate { userId ->
                userId to histRepository.getHist(userId)
            }
            hist.forEach { (userId, messages) ->
                chatHistories[userId] = LinkedBlockingDeque(messages.map { it.toChatMessage() })
            }

            log.info("Fetched ${hist.values.sumOf { it.size }} histories for ${hist.keys.size} users")
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