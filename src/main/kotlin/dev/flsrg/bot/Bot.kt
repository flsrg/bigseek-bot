package dev.flsrg.bot

import dev.flsrg.bot.uitls.BotUtils.KeyboardMarkupClearHistory
import dev.flsrg.bot.uitls.BotUtils.KeyboardMarkupStop
import dev.flsrg.bot.uitls.BotUtils.botMessage
import dev.flsrg.bot.uitls.BotUtils.withRetry
import dev.flsrg.bot.uitls.MessageProcessor
import dev.flsrg.llmpollingclient.client.OpenRouterClient
import dev.flsrg.llmpollingclient.client.OpenRouterDeepseekConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.util.concurrent.ConcurrentHashMap

class Bot(botToken: String?) : TelegramLongPollingBot(botToken) {
    companion object {
        private const val CALLBACK_DATA_FORCE_STOP = "FORCESTOP"
        private const val CALLBACK_DATA_CLEAR_HISTORY = "CLEARHISTORY"
        private const val STOP_MESSAGE = "User requested stop"

        private const val START_DEFAULT_COMMAND = "/start"
    }

    private val apiKey = System.getenv("OPENROUTER_API_KEY")!!

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val chatScopes = ConcurrentHashMap<String, CoroutineScope>()
    private val rootScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val chatJobs = ConcurrentHashMap<String, Job>()

    private val openRouterDeepseekClient = OpenRouterClient(OpenRouterDeepseekConfig(apiKey = apiKey))
    override fun getBotUsername() = "Bigdick"

    override fun onRegister() {
        super.onRegister()
    }

    override fun onUpdateReceived(update: Update) {
        rootScope.launch {
            if (update.hasMessage() && update.message.hasText()) {
                when {
                    isStartMessage(update.message) -> handleStartMessage(update.message.chat.id.toString())
                    else -> handleMessage(update)
                }
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update)
            }
        }
    }

    private fun isStartMessage(message: Message): Boolean {
        return message.text == START_DEFAULT_COMMAND
    }

    private fun handleStartMessage(chatId: String) {
        execute(botMessage(chatId, "Го"))
    }

    private fun handleMessage(update: Update) {
        val startMillis = System.currentTimeMillis()
        var reasoningSize = 0
        var contentSize = 0

        val userMessage = update.message.text
        val chatId = update.message.chat.id.toString()

        val chatScope = chatScopes.getOrPut(chatId) {
            CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("Chat-$chatId"))
        }
        chatJobs[chatId]?.cancel(CancellationException("New message in chat"))

        val newJob = chatScope.launch {
            execute(SendMessage(chatId, "Думаю..."))
            log.info("Responding to ${update.message.from.userName}")
            val messageProcessor = MessageProcessor(this@Bot, chatId)

            try {
                withRetry(origin = "job askDeepseekR1") {
                    messageProcessor.deleteAllReasoningMessages()
                    askDeepseekR1(chatId, userMessage, messageProcessor)
                }
            } catch (e: Exception) {
                if (isStopException(e)) {
                    execute(botMessage(chatId, "Стою"))
                } else {
                    execute(botMessage(chatId, "error: ${e.message}"))
                }

                log.error("Error processing message", e)

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

    private suspend fun askDeepseekR1(chatId: String, userMessage: String, messageProcessor: MessageProcessor) {
        openRouterDeepseekClient.askChat(chatId, message = userMessage)
            .onEach { message ->
                messageProcessor.processMessage(message)
            }
            .sample(BotConfig.MESSAGE_SAMPLING_DURATION)
            .onCompletion { exception ->
                if (exception != null) throw exception
                messageProcessor.updateOrSend(KeyboardMarkupClearHistory())
            }
            .collect {
                messageProcessor.updateOrSend(KeyboardMarkupStop(), KeyboardMarkupClearHistory())
            }
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
                job.cancel(CancellationException(STOP_MESSAGE))

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

    private fun isStopException(exception: Throwable): Boolean {
        return exception is CancellationException && exception.message == STOP_MESSAGE
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
                    .build()
            )
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }
}