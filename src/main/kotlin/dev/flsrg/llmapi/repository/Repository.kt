package dev.flsrg.llmapi.repository

import dev.flsrg.llmapi.Config
import dev.flsrg.llmapi.model.ChatMessage
import dev.flsrg.llmapi.model.ChatResponse
import kotlinx.coroutines.flow.Flow

interface Repository {
    fun getCompletionsStream(
        apiKey: String,
        model: Config.Model,
        chatMessages: List<ChatMessage>
    ): Flow<ChatResponse>
}