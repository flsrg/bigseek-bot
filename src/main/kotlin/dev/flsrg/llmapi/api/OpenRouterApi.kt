package dev.flsrg.llmapi.api

import dev.flsrg.llmapi.Config
import dev.flsrg.llmapi.client.ClientConfig
import dev.flsrg.llmapi.model.ChatMessage
import dev.flsrg.llmapi.model.ChatRequest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

class OpenRouterApi: Api {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun getCompletionsStream(config: ClientConfig, messages: List<ChatMessage>) = flow<HttpResponse> {
        val requestPayload = ChatRequest(
            model = config.model.id,
            chainOfThought = config.chainOfThoughts,
            stream = config.chainOfThoughts,
            messages = messages.toList()
        )

        log.info("Requesting completions from OpenRouter (payload: {})", requestPayload)

        Config.sreamingClient.preparePost(config.baseUrl) {
            headers {
                append(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                append(HttpHeaders.ContentType, "application/json")
                if (config.chainOfThoughts) append(HttpHeaders.Accept, "text/event-stream")
            }
            setBody(requestPayload)
        }.execute { response ->
            emit(response)
        }
    }
}