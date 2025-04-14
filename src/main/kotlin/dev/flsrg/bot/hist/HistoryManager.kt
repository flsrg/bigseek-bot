package dev.flsrg.bot.hist

import dev.flsrg.bot.BotConfig
import dev.flsrg.bot.db.Database
import dev.flsrg.bot.db.HistMessage
import dev.flsrg.bot.repo.ChatHistRepository
import dev.flsrg.bot.repo.UserRepository
import dev.flsrg.llmpollingclient.model.ChatMessage
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque
import kotlin.math.min

class HistoryManager(
    private val botConfig: BotConfig,
    private val histRepository: ChatHistRepository,
    usersRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val chatHistories = ConcurrentHashMap<Long, LinkedBlockingDeque<ChatMessage>>()

    init {
        // Prefetch existing histories from database on startup
        transaction(histRepository.database) {
            val userIdsToFetch = usersRepository.getActiveUsers(14).map { it.id }
            val hist = userIdsToFetch.associateWith { userId -> histRepository.getHist(userId) }
            hist.forEach { (userId, messages) ->
                chatHistories[userId] = LinkedBlockingDeque(messages.map { it.toChatMessage() })
            }

            log.info("Fetched ${hist.values.sumOf { it.size }} histories for ${hist.keys.size} users")
        }
    }

    fun getHistory(userId: Long): List<ChatMessage> {
        return chatHistories[userId]?.toList()
            ?: histRepository.getHist(userId).map { it.toChatMessage() }.also {
                chatHistories[userId] = LinkedBlockingDeque(it)
            }
    }

    fun addMessage(userId: Long, message: ChatMessage) {
        val hist = chatHistories.getOrPut(userId) { LinkedBlockingDeque() }
        hist.addLast(message)

        var histToDrop = 0
        if (hist.size > botConfig.maxHistorySize) {
            histToDrop = min(hist.size - botConfig.maxHistorySize, 0)
            hist.drop(histToDrop)
        }

        transaction(Database.database) {
            histRepository.addMessage(userId, message.toHistMessage())

            if (histToDrop > 0) {
                histRepository.drop(userId, histToDrop)
            }
        }

    }

    fun clearHistory(userId: Long) {
        chatHistories.remove(userId)
        histRepository.clearHistory(userId)
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