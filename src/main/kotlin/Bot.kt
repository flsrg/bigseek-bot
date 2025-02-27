package dev.flsrg

import dev.flsrg.model.ChatMessage
import dev.flsrg.model.ChatRequest
import dev.flsrg.model.ChatResponse
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update

class Bot(botToken: String?) : TelegramLongPollingBot(botToken) {
    companion object {
        private const val REQUEST_TIMEOUT = 10 * 60 * 1000L
    }

    //TODO: Move to system environment
    private val API_KEY = "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"
    private val log: Logger = LoggerFactory.getLogger(javaClass)
    private val format = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        engine {
            requestTimeout = REQUEST_TIMEOUT
        }
    }

    override fun getBotUsername() = "Bigdick"

    override fun onRegister() {
        super.onRegister()
    }

    override fun onUpdateReceived(update: Update) {
        if (update.hasMessage() && update.message.hasText()) {
            val userMessage = update.message.text
            val chatId = update.message.chat.id
            log.info("userMessage: $userMessage")

            val requestPayload = ChatRequest(
                model = "deepseek/deepseek-r1:free",
                chainOfThought = true,
                messages = listOf(ChatMessage(role = "user", content = userMessage))
            )

            runBlocking {
                execute(SendMessage(chatId.toString(), "Думаю..."))

                try {
                    val response: HttpResponse  = client.post("https://openrouter.ai/api/v1/chat/completions") {
                        headers {
                            append("Authorization", "Bearer $API_KEY")
                            append("Content-Type", "application/json")
                            append("chain_of_thought", "true")
                        }
                        setBody(requestPayload)
                    }

                    val responseText = response.bodyAsText()
                    println(responseText)

                    val chatResponse: ChatResponse = format.decodeFromString(ChatResponse.serializer(), response.bodyAsText())
                    log.info("Chat message: $chatResponse")

                    val reasoning = chatResponse.choices.first().message.reasoning
                    if (reasoning != null) {
                        execute(botMessage(chatId.toString(), reasoning))
                    }

                    val message = chatResponse.choices.first().message.content
                    require(message.isNotEmpty()) { "Doesn't answer anything again" }
                    execute(botMessage(chatId.toString(), message))

                } catch (e: Exception) {
                    execute(botMessage(chatId.toString(), "error: ${e.message}"))
                    throw e
                }
            }
        }
    }

    private fun botMessage(chatId: String, message: String): SendMessage {
        return SendMessage().apply {
            this.chatId = chatId
            this.text = message
            this.parseMode = "Markdown"
        }
    }
}