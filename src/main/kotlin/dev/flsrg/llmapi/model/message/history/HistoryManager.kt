package dev.flsrg.llmapi.model.message.history

import dev.flsrg.llmapi.Config
import dev.flsrg.llmapi.model.ChatMessage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque

class HistoryManager {
    private val chatHistories = ConcurrentHashMap<String, LinkedBlockingDeque<ChatMessage>>()

    fun getHistory(chatId: String): List<ChatMessage> {
        return chatHistories[chatId]?.toList() ?: emptyList()
    }

    fun addMessage(chatId: String, message: ChatMessage) {
        chatHistories
            .getOrPut(chatId) { LinkedBlockingDeque() }
            .apply {
                addLast(message)
                while (size > Config.MAX_CHAT_HISTORY_LENGTH) removeFirst()
            }
    }

    fun clearHistory(chatId: String) {
        chatHistories.remove(chatId)
    }

    fun removeLast(chatId: String) {
        chatHistories[chatId]?.removeLast()
    }
}