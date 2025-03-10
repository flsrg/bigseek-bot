package dev.flsrg.bot

import dev.flsrg.bot.uitls.BotUtils.botMessage
import dev.flsrg.bot.uitls.MessageProcessor
import dev.flsrg.llmapi.client.OpenRouterClient
import dev.flsrg.llmapi.client.OpenRouterDeepseekConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.future.await
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.util.concurrent.ConcurrentHashMap

class Bot(botToken: String?) : TelegramLongPollingBot(botToken) {
    companion object {
        //TODO: Move to system environment
        private const  val API_KEY = "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"

        private const val CALLBACK_DATA_FORCE_STOP = "FORCESTOP"
        private const val CALLBACK_DATA_CLEAR_HISTORY = "CLEARHISTORY"

        private const val START_DEFAULT_COMMAND = "/start"
    }

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val chatScopes = ConcurrentHashMap<String, CoroutineScope>()
    private val rootScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val chatJobs = ConcurrentHashMap<String, Job>()

    private val openRouterDeepseekClient = OpenRouterClient(OpenRouterDeepseekConfig(apiKey = API_KEY))

    private val keyboardMarkup by lazy { createControlKeyboard() }

    override fun getBotUsername() = "Bigdick"

    override fun onRegister() {
        super.onRegister()
    }

    override fun onUpdateReceived(update: Update) {
        rootScope.launch {
            when {
                update.hasMessage() && update.message.hasText() -> handleMessage(update)
                update.hasCallbackQuery() -> handleCallbackQuery(update)
            }
        }
    }

    private fun handleMessage(update: Update) {
        val startMillis = System.currentTimeMillis()
        var reasoningSize = 0
        var contentSize = 0

        val userMessage = update.message.text
        val chatId = update.message.chat.id.toString()

        if (userMessage == START_DEFAULT_COMMAND || userMessage.isEmpty()) {
            execute(botMessage(chatId, "Го"))
            return
        }

        val chatScope = chatScopes.getOrPut(chatId) {
            CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("Chat-$chatId"))
        }
        chatJobs[chatId]?.cancel(CancellationException("New message in chat"))

        val newJob = chatScope.launch {
            executeAsync(SendMessage(chatId, "Думаю...")).await()
            log.info("Responding to ${update.message.from.userName}")

            try {
                val messageProcessor = MessageProcessor()

                log.debug("{} asked: {}", update.message.from.id, userMessage)

                openRouterDeepseekClient.askChat(chatId, userMessage)
                    .onCompletion {
                        messageProcessor.finishMessage(this@Bot, chatId, keyboardMarkup)
                    }.collect { response ->
                        if (!isActive) return@collect

                        messageProcessor.processStream(
                            bot = this@Bot,
                            chatId = chatId,
                            chatResponse = response,
                            keyboardMarkup = keyboardMarkup,
                        )

                        reasoningSize += response.choices.firstOrNull()?.delta?.reasoning?.length ?: 0
                        contentSize += response.choices.firstOrNull()?.delta?.content?.length ?: 0
                    }

            } catch (e: Exception) {
                // Repeat
                if (e is OpenRouterClient.ExceptionEmptyResponse) {
                    handleMessage(update)
                    chatJobs.remove(chatId)
                    log.error("Error processing message ${e.message} repeat")
                } else {
                    execute(botMessage(chatId, "error: ${e.message}"))
                    log.error("Error processing message", e)
                }

            } finally {
                chatJobs.remove(chatId)

                log.info("Responding to ${update.message.from.userName} completed " +
                        "(${System.currentTimeMillis() - startMillis}ms) " +
                        "(" +
                        "reasoning size = $reasoningSize, " +
                        "content.size = $contentSize" +
                        ")")
            }
        }

        chatJobs[chatId] = newJob
    }

    private fun handleCallbackQuery(update: Update) {
        val callback = update.callbackQuery
        val chatId = callback.message.chatId.toString()
        val callbackId = callback.id

        when (callback.data) {
            CALLBACK_DATA_FORCE_STOP -> {
                forceStop(chatId, callbackId)
            }
            CALLBACK_DATA_CLEAR_HISTORY -> {
                forceStop(chatId, callbackId)
                clearHistory(chatId, callbackId)
            }
        }
    }

    private fun forceStop(chatId: String, callbackId: String) {
        val job = chatJobs[chatId]

        try {
            if (job != null) {
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
        openRouterDeepseekClient.clearHistory(chatId)

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
}