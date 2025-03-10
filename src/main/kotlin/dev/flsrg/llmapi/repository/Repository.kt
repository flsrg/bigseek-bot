package dev.flsrg.llmapi.repository

import dev.flsrg.llmapi.client.ClientConfig
import dev.flsrg.llmapi.model.ChatMessage
import dev.flsrg.llmapi.model.ChatResponse
import kotlinx.coroutines.flow.Flow

interface Repository {
    fun getCompletionsStream(config: ClientConfig, chatMessages: List<ChatMessage>): Flow<ChatResponse>
}