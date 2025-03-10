package dev.flsrg.llmapi.api

import dev.flsrg.llmapi.client.ClientConfig
import dev.flsrg.llmapi.model.ChatMessage
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.flow.Flow

interface Api {
    fun getCompletionsStream(config: ClientConfig, messages: List<ChatMessage>): Flow<HttpResponse>
}