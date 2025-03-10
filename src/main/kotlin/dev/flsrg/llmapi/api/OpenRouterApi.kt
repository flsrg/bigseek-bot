package dev.flsrg.llmapi.api

import dev.flsrg.llmapi.Config
import dev.flsrg.llmapi.model.ChatRequest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

class OpenRouterApi: Api {
    companion object {
        private const val API_URL = "https://openrouter.ai/api/v1/chat/completions"
    }

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getCompletionsStream(apiKey: String, requestPayload: ChatRequest) = flow<HttpResponse> {
        log.debug("Requesting completions from OpenRouter (payload: {})", requestPayload)

        Config.client.preparePost(API_URL) {
            headers {
                append(HttpHeaders.Authorization, "Bearer $apiKey")
                append(HttpHeaders.ContentType, "application/json")
                append(HttpHeaders.Accept, "text/event-stream")
            }
            setBody(requestPayload)
        }.execute { response ->
            emit(response)
        }
    }
}