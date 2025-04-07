package dev.flsrg.bot

import dev.flsrg.bot.MessageHelper.Companion.isStartMessage
import dev.flsrg.bot.MessageHelper.Companion.isThinkingMessage
import dev.flsrg.bot.repo.SQLUsersRepository
import dev.flsrg.bot.roleplay.LanguageDetector
import dev.flsrg.bot.roleplay.LanguageDetector.Language.RU
import dev.flsrg.bot.roleplay.RoleConfig
import dev.flsrg.bot.roleplay.RoleDetector
import dev.flsrg.bot.uitls.BotUtils
import dev.flsrg.bot.uitls.BotUtils.KeyboardMarkupClearHistory
import dev.flsrg.bot.uitls.BotUtils.KeyboardMarkupStop
import dev.flsrg.bot.uitls.BotUtils.botMessage
import dev.flsrg.bot.uitls.BotUtils.sendTypingAction
import dev.flsrg.bot.uitls.BotUtils.withRetry
import dev.flsrg.bot.uitls.MessageProcessor
import dev.flsrg.llmpollingclient.client.OpenRouterClient
import dev.flsrg.llmpollingclient.client.OpenRouterConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.util.concurrent.ConcurrentHashMap

@OptIn(FlowPreview::class)
class Bot(botToken: String?, adminUserId: Long) : TelegramLongPollingBot(botToken) {
    companion object {
        private const val CALLBACK_DATA_FORCE_STOP = "FORCESTOP"
        private const val CALLBACK_DATA_CLEAR_HISTORY = "CLEARHISTORY"
        private const val JOB_CLEANUP_INTERVAL = 5 * 60 * 1000L
    }

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val apiKey = System.getenv("OPENROUTER_API_KEY")!!
    private val openRouterDeepseekClient = OpenRouterClient(OpenRouterConfig(apiKey = apiKey))
    private val messageHelper = MessageHelper(this)
    private val usrRepo = SQLUsersRepository()
    private val adminHelper = AdminHelper(this, adminUserId, usrRepo)
    private val roleDetector = RoleDetector(RoleConfig.allRoles)

    private val rootScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val chatJobs = ConcurrentHashMap<String, Job>()
    private val rateLimits = ConcurrentHashMap<String, Long>()

    private var currentLanguage = RU

    // Cleanup mechanism to remove completed jobs
    init {
        rootScope.launch {
            while (isActive) {
                delay(JOB_CLEANUP_INTERVAL) // Run every 5 minutes
                chatJobs.entries.removeAll { (_, job) -> job.isCompleted }
            }
        }
    }

    override fun getBotUsername() = "Bigdick"

    override fun onRegister() {
        super.onRegister()
    }

    override fun onUpdateReceived(update: Update) {
        rootScope.launch(Dispatchers.IO) {
            if (update.hasMessage() && update.message.hasText()) {
                val message = update.message
                val chatId = message.chat.id.toString()

                when {
                    adminHelper.isAdminCommand(update) -> adminHelper.handleAdminCommand(update)
                    isStartMessage(message) -> handleStartMessage(chatId)
                    isThinkingMessage(message) -> handleMessage(true, update)
                    else -> handleMessage(false, update)
                }
            } else if (update.hasCallbackQuery()) {
                handleCallbackQuery(update)
            }
        }
    }

    private fun handleStartMessage(chatId: String) {
        execute(botMessage(chatId, "Го"))
    }

    private fun handleMessage(isThinking: Boolean, update: Update) {
        val startMillis = System.currentTimeMillis()
        var contentSize = 0

        val userId = update.message.from.id
        val chatId = update.message.chat.id.toString()
        val userName = update.message.from.userName
        val userMessage = update.message.text

        if (startMillis - rateLimits.getOrDefault(chatId, 0) < BotConfig.MESSAGE_RATE_LIMIT) {
            messageHelper.sendRateLimitMessage(chatId, currentLanguage)
            return
        }
        rateLimits[chatId] = startMillis

        chatJobs[chatId]?.cancel(BotUtils.NewMessageStopException())

        val newJob = rootScope.launch {
            sendTypingAction(chatId)
            currentLanguage = LanguageDetector.detectLanguage(userMessage)
            messageHelper.sendRespondingMessage(chatId, isThinking, currentLanguage)

            val messageProcessor = MessageProcessor(this@Bot, chatId)
            log.info("Responding (${if (isThinking) "R1" else "V3"}) to ${update.message.from.userName}")

            try {
                withRetry(origin = "job askDeepseekR1") {
                    messageProcessor.deleteAllReasoningMessages()
                    messageProcessor.clear()
                    adminHelper.updateUserMessage(userId, userName, OpenRouterClient.ChatMessage(role = "user", content = userMessage))

                    val finalAssistantMessage = askDeepseek(chatId, isThinking, userMessage, messageProcessor, currentLanguage)
                    adminHelper.updateUserMessage(userId, userName, finalAssistantMessage)
                }
            } catch (e: Exception) {
                val errorMessage = BotUtils.errorToMessage(e, currentLanguage)
                execute(botMessage(chatId, errorMessage))

                log.error("Error processing message", e)

            } finally {
                chatJobs.remove(chatId)
                log.info("Responding to ${update.message.from.userName} completed " +
                        "(${System.currentTimeMillis() - startMillis}ms) " +
                        "(" +
                        "content.size = $contentSize" +
                        ")")
            }
        }

        chatJobs[chatId] = newJob
    }

    private suspend fun askDeepseek(
        chatId: String,
        isThinking: Boolean,
        userMessage: String,
        messageProcessor: MessageProcessor,
        language: LanguageDetector.Language,
    ): OpenRouterClient.ChatMessage {
        val model = if (isThinking) OpenRouterConfig.DEEPSEEK_R1 else OpenRouterConfig.DEEPSEEK_V3
        var finalAssistantMessage: OpenRouterClient.ChatMessage? = null
        val role = roleDetector.detectRole(userMessage, language)
        log.info("Role detected: $role for $language")

        openRouterDeepseekClient.askChat(
            chatId = chatId,
            model = model,
            message = userMessage,
            systemMessage = if (language == RU) role.russianSystemMessage else role.systemMessage,
        ).onEach { message ->
            if (!messageIsEmpty(message)) messageHelper.cleanupRespondingMessageButtons(chatId)
            messageProcessor.processMessage(message)
        }
        .sample(BotConfig.MESSAGE_SAMPLING_DURATION)
        .onCompletion { exception ->
            if (exception != null) throw exception
            messageProcessor.updateOrSend(
                KeyboardMarkupClearHistory(currentLanguage)
            )
            finalAssistantMessage = OpenRouterClient.ChatMessage(
                role = "assistant" ,
                content = messageProcessor.getFinalAssistantMessage()
            )
        }
        .collect {
            sendTypingAction(chatId)
            messageProcessor.updateOrSend(
                KeyboardMarkupStop(currentLanguage),
                KeyboardMarkupClearHistory(currentLanguage)
            )
        }

        return finalAssistantMessage!!
    }

    private fun messageIsEmpty(message: OpenRouterClient.ChatResponse): Boolean {
        return message.choices.firstOrNull()?.delta?.reasoning?.isEmpty() != false
                && message.choices.firstOrNull()?.delta?.content?.isEmpty() != false
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
                job.cancel(BotUtils.UserStoppedException())

                execute(
                    AnswerCallbackQuery.builder()
                        .callbackQueryId(callbackId)
                        .text(Strings.CallbackStopSuccessAnswer.get(currentLanguage))
                        .build()
                )
            } else {
                execute(
                    AnswerCallbackQuery.builder()
                        .callbackQueryId(callbackId)
                        .text(Strings.CallbackStopNothingRunningAnswer.get(currentLanguage))
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
                .text(Strings.CallbackClearHistorySuccessAnswer.get(currentLanguage))
                .build()
        )

        // Optionally, send a message to the chat confirming the history is cleared
        try {
            execute(
                SendMessage.builder()
                    .chatId(chatId)
                    .text(Strings.CallbackClearHistorySuccessMessage.get(currentLanguage))
                    .build()
            )
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }
}