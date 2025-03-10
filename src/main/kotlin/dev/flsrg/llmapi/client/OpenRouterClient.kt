package dev.flsrg.llmapi.client

import dev.flsrg.llmapi.api.OpenRouterApi
import dev.flsrg.llmapi.model.ChatMessage
import dev.flsrg.llmapi.model.ChatResponse
import dev.flsrg.llmapi.model.message.history.HistoryManager
import dev.flsrg.llmapi.repository.OpenRouterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach

class OpenRouterClient(config: ClientConfig): Client(config) {
    private val api = OpenRouterApi()
    private val repository = OpenRouterRepository(api)
    override val histManager = HistoryManager(config)

    override fun askChat(chatId: String, message: String, rememberHistory: Boolean): Flow<ChatResponse> {
        val newMessage = ChatMessage(role = "user", content = message)
        val payload: List<ChatMessage> = if (rememberHistory) {
            histManager.addMessage(chatId, newMessage)
            histManager.getHistory(chatId)
        } else {
            listOf(newMessage)
        }

        val contentBuffer = StringBuilder()

        return repository.getCompletionsStream(config, payload).onEach { response ->
            val content = response.choices.first().delta?.content
            if (rememberHistory && content != null) {
                contentBuffer.append(response.choices.first().delta?.content)
            }
        }.onCompletion {
            if (rememberHistory) {
                val assistantMessage = ChatMessage(role = "assistant", content = contentBuffer.toString())
                if (assistantMessage.content.isEmpty()) {
                    histManager.removeLast(chatId)
                    throw ExceptionEmptyResponse()
                }

                histManager.addMessage(chatId, assistantMessage)
                contentBuffer.clear()
            }
        }
    }

    class ExceptionEmptyResponse() : Exception("Empty response")
}