package dev.flsrg

import dev.flsrg.model.ChatMessage
import dev.flsrg.model.ChatRequest
import dev.flsrg.model.ChatResponse
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException
import java.util.concurrent.ConcurrentHashMap

class Bot(botToken: String?) : TelegramLongPollingBot(botToken) {
    companion object {
        //TODO: Move to system environment
        private const  val API_KEY = "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"

        private const val CONNECTION_TIMEOUT = 10 * 60 * 1000L
        private const val MESSAGE_MAX_LENGTH = 3000
        private const val MARKDOWN_PARSE_ERROR_MESSAGE = "can't parse entities: Can't find end of the entity starting at byte offset"
        private const val MESSAGE_THE_SAME_ERROR_MESSAGE = "message is not modified: specified new message content and reply markup are exactly the same as a current content and reply markup of the message"

        private const val MAX_HISTORY_LENGTH = 10

        private const val CALLBACK_DATA_FORCE_STOP = "FORCESTOP"
        private const val CALLBACK_DATA_CLEAR_HISTORY = "CLEARHISTORY"
    }

    private val log: org.slf4j.Logger = LoggerFactory.getLogger(javaClass)
    private val format = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val botScope = CoroutineScope(Dispatchers.Default + Job())
    private val chatHistories = ConcurrentHashMap<String, MutableList<ChatMessage>>()

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(format)
        }
        install(HttpTimeout) {
            // Disable or extend timeouts for streaming connections:
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS  // 0 means no timeout
            socketTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS   // disable socket timeout if necessary
            connectTimeoutMillis = CONNECTION_TIMEOUT  // adjust as needed
        }
        install(SSE)
    }

    override fun getBotUsername() = "Bigdick"

    override fun onRegister() {
        super.onRegister()
    }

    override fun onUpdateReceived(update: Update) {
        when {
            update.hasMessage() && update.message.hasText() -> handleMessage(update)
            update.hasCallbackQuery() -> handleCallbackQuery(update)
        }
    }

    private fun handleMessage(update: Update) {
        val startMillis = System.currentTimeMillis()

        val userMessage = update.message.text
        val chatId = update.message.chat.id.toString()

        val history = chatHistories.getOrPut(chatId) { mutableListOf() }

        history.add(ChatMessage(role = "user", content = userMessage))
        while (history.size > MAX_HISTORY_LENGTH) {
            history.removeAt(0)
        }
        log.info("${update.message.from.userName} asks: ${history.joinToString("\n")}")

        activeJobs[chatId]?.cancel(CancellationException("User started new chat"))

        val newJob = botScope.launch {

            val requestPayload = ChatRequest(
                model = "deepseek/deepseek-r1:free",
                chainOfThought = true,
                messages = history.toList(),
                stream = true
            )

            execute(SendMessage(chatId.toString(), "Думаю..."))

            val buffer = StringBuilder()
            val contentBuffer = StringBuilder()
            val reasoningBuffer = StringBuilder()
            val fullContentBuffer = StringBuilder()
            var contentMessageId: Int? = null
            var reasoningMessageId: Int? = null

            try {
                client.preparePost("https://openrouter.ai/api/v1/chat/completions") {
                    headers {
                        append("Authorization", "Bearer $API_KEY")
                        append("Content-Type", "application/json")
                        append("Accept", "text/event-stream")
                    }
                    setBody(requestPayload)
                }.execute { response ->
                    log.info("Responding to ${update.message.from.userName}")
                    val channel: ByteReadChannel = response.bodyAsChannel()

                    while (!channel.isClosedForRead && isActive) {
                        val line = channel.readUTF8Line() ?: break

                        if (line.startsWith("data: ")) {
                            val json = line.removePrefix("data: ").trim()
                            if (json == "[DONE]") break

                            val responseObj = format.decodeFromString<ChatResponse>(json)
                            val delta = responseObj.choices.firstOrNull()?.delta

                            if (delta != null) {
                                val isReasoning = !delta.reasoning.isNullOrEmpty()
                                val isResponding = !delta.content.isNullOrEmpty()

                                if (isReasoning) {
                                    buffer.append(delta.reasoning)
                                } else if (isResponding) {
                                    buffer.append(delta.content)
                                }

                                val isParagraph = if (isReasoning) {
                                    delta.reasoning.contains("\n")
                                } else {
                                    delta.content?.contains("\n") == true
                                }

                                if (isParagraph) {
                                    if (isReasoning) {
                                        reasoningBuffer.append(buffer.toString())
                                        val reasoningMessage = reasoningBuffer.toString()

                                        if (reasoningBuffer.length > MESSAGE_MAX_LENGTH) {
                                            reasoningMessageId = null
                                            reasoningBuffer.clear()
                                        }

                                        reasoningMessageId = updateOrSendMessage(
                                            chatId = chatId,
                                            message = reasoningMessage,
                                            existingMessageId = reasoningMessageId,
                                        )
                                    } else if (isResponding) {
                                        fullContentBuffer.append(buffer.toString())

                                        contentBuffer.append(buffer.toString())
                                        val contentMessage = contentBuffer.toString()

                                        if (contentBuffer.length > MESSAGE_MAX_LENGTH) {
                                            contentMessageId = null
                                            contentBuffer.clear()
                                        }

                                        contentMessageId = updateOrSendMessage(
                                            chatId = chatId,
                                            message = contentMessage,
                                            existingMessageId = contentMessageId,
                                        )
                                    }

                                    buffer.clear()
                                }
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                execute(botMessage(chatId, "error: ${e.message}"))
                log.info("Response (reasoning = $reasoningBuffer, content = $contentBuffer)")

            } finally {
                val fullContentMessage = fullContentBuffer.toString()
                if (fullContentMessage.isNotEmpty()) {
                    val history = chatHistories.getOrPut(chatId) { mutableListOf() }

                    history.add(ChatMessage(role = "assistant", content = fullContentMessage))
                    while (history.size > MAX_HISTORY_LENGTH) {
                        history.removeAt(0)
                    }
                }

                activeJobs.remove(chatId)

                log.info("Responding to ${update.message.from.userName} completed " +
                        "(${System.currentTimeMillis() - startMillis}ms) " +
                        "(" +
                        "reasoning size = ${reasoningBuffer.length}, " +
                        "content.size = ${contentBuffer.length}" +
                        ")")

                if (contentBuffer.isEmpty() && reasoningBuffer.isEmpty()) {
                    execute(botMessage(chatId, "Плохой провайдер, попробуйте еще раз"))
                }
                if (buffer.isNotEmpty()) {
                    contentBuffer.append(buffer.toString())
                    updateOrSendMessage(chatId, contentBuffer.toString(), contentMessageId)
                }
            }
        }

        activeJobs[chatId] = newJob
    }

    private fun handleCallbackQuery(update: Update) {
        val callback = update.callbackQuery
        val chatId = callback.message.chatId.toString()
        val callbackId = callback.id

        when (callback.data) {
            CALLBACK_DATA_FORCE_STOP -> forceStop(chatId, callbackId)
            CALLBACK_DATA_CLEAR_HISTORY -> {
                forceStop(chatId, callbackId)
                clearHistory(chatId, callbackId)
            }
        }
    }

    private fun forceStop(chatId: String, callbackId: String) {
        val job = activeJobs[chatId]

        try {
            if (job != null && job.isActive) {
                job.cancel(CancellationException("User requested stop"))
                execute(
                    AnswerCallbackQuery.builder()
                        .callbackQueryId(callbackId)
                        .text("Остановился!")
                        .build()
                )
            } else {
                execute(
                    AnswerCallbackQuery.builder()
                        .callbackQueryId(callbackId)
                        .text("Нечего останавливать")
                        .build()
                )
            }
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    private fun clearHistory(chatId: String, callbackId: String) {
        // Clear the chat history
        chatHistories.remove(chatId)

        // Send confirmation to the user
        execute(
            AnswerCallbackQuery.builder()
                .callbackQueryId(callbackId)
                .text("History cleared!")
                .build()
        )

        // Optionally, send a message to the chat confirming the history is cleared
        try {
            execute(
                SendMessage.builder()
                    .chatId(chatId)
                    .text("Бот забыл историю! Давай по новой Миша")
                    .replyMarkup(createControlKeyboard())
                    .build()
            )
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    private fun createControlKeyboard(): InlineKeyboardMarkup {
        return InlineKeyboardMarkup.builder()
            .keyboard(
                listOf(
                    listOf(
                        InlineKeyboardButton.builder()
                            .text("🚫 Остановись")
                            .callbackData(CALLBACK_DATA_FORCE_STOP)
                            .build(),
                        InlineKeyboardButton.builder()
                            .text("🧹 Забудь все")
                            .callbackData(CALLBACK_DATA_CLEAR_HISTORY)
                            .build()
                    )
                )
            )
            .build()
    }

    /**
     * @return existing active editable message id
     */
    private fun updateOrSendMessage(
        chatId: String,
        message: String,
        existingMessageId: Int?
    ): Int? {
        if (message.isEmpty()) return existingMessageId

        try {
            if (existingMessageId == null) {
                val newMessage = botMessage(chatId, message)
                return execute(newMessage).messageId
            } else {
                val editMessage = editMessage(chatId, existingMessageId, message)
                execute(editMessage)

                return existingMessageId
            }

        } catch (exception: TelegramApiRequestException) {
            if (exception.message?.contains(MARKDOWN_PARSE_ERROR_MESSAGE) == true) {
                log.info("Markdown parse error, skip message")
                return existingMessageId
            } else if (exception.message?.contains(MESSAGE_THE_SAME_ERROR_MESSAGE) == true) {
                log.info("Message the same error, skip message")
                return existingMessageId
            }
            else throw exception
        }
    }

    private fun editMessage(chatId: String, messageId: Int, message: String): EditMessageText {
        return EditMessageText.builder()
            .chatId(chatId)
            .messageId(messageId)
            .text(message)
            .parseMode("Markdown")
            .replyMarkup(createControlKeyboard())
            .build()
    }

    private fun botMessage(chatId: String, message: String): SendMessage {
        return SendMessage.builder()
            .chatId(chatId)
            .text(message)
            .parseMode("Markdown")
            .build()
    }
}