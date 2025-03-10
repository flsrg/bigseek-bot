package dev.flsrg.llmapi.client

import dev.flsrg.llmapi.Config
import dev.flsrg.llmapi.model.ChatMessage
import dev.flsrg.llmapi.model.ChatResponse
import dev.flsrg.llmapi.model.message.history.HistoryManager
import kotlinx.coroutines.flow.Flow

abstract class Client(internal val apiKey: String, internal val model: Config.Model) {
    abstract val histManager: HistoryManager

    abstract fun askChat(chatId: String, message: String, rememberHistory: Boolean = true): Flow<ChatResponse>

    open fun getHistory(chatId: String): List<ChatMessage> = histManager.getHistory(chatId)
    open fun clearHistory(chatId: String) = histManager.clearHistory(chatId)
}