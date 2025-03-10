package dev.flsrg.llmapi.repository

import dev.flsrg.llmapi.Config
import dev.flsrg.llmapi.api.Api
import dev.flsrg.llmapi.client.ClientConfig
import dev.flsrg.llmapi.model.ChatMessage
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

    override fun getCompletionsStream(config: ClientConfig, chatMessages: List<ChatMessage>): Flow<ChatResponse> {
        return flow<ChatResponse> {
            api.getCompletionsStream(config, chatMessages).collect { response ->
                log.debug("Received API response (code={})", response.status.value)

                val channel: ByteReadChannel = response.bodyAsChannel()
                val isScopeActive = currentCoroutineContext().isActive

                while (!channel.isClosedForRead && isScopeActive) {
                    val line = channel.readUTF8Line() ?: break

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