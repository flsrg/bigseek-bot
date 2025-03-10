package dev.flsrg.llmapi.repository

import dev.flsrg.llmapi.Config
import dev.flsrg.llmapi.api.Api
import dev.flsrg.llmapi.model.ChatMessage
import dev.flsrg.llmapi.model.ChatRequest
import dev.flsrg.llmapi.model.ChatResponse
import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import org.slf4j.LoggerFactory

class OpenRouterRepository(private val api: Api): Repository {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun getCompletionsStream(
        apiKey: String,
        model: Config.Model,
        chatMessages: List<ChatMessage>
    ): Flow<ChatResponse> {

        val payload = ChatRequest(
            model = model.id,
            chainOfThought = true,
            messages = chatMessages.toList(),
            stream = true
        )

        return flow<ChatResponse> {
            api.getCompletionsStream(apiKey, requestPayload = payload).collect { response ->
                log.debug("Received API response (code={})", response.status.value)

                val channel: ByteReadChannel = response.bodyAsChannel()
                val isScopeActive = currentCoroutineContext().isActive

                while (!channel.isClosedForRead && isScopeActive) {
                    val line = channel.readUTF8Line()
                    if (line == null) break

                    if (line.startsWith("data: ")) {
                        val json = line.removePrefix("data: ").trim()
                        if (json == "[DONE]") break

                        val chatResponse = Config.format.decodeFromString<ChatResponse>(json)
                        emit(chatResponse)
                    }
                }
            }
        }
    }
}