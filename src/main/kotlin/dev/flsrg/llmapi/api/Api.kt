package dev.flsrg.llmapi.api

import dev.flsrg.llmapi.model.ChatRequest
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.flow.Flow

interface Api {
    fun getCompletionsStream(
        apiKey: String,
        requestPayload: ChatRequest,
    ): Flow<HttpResponse>
}