package dev.flsrg.bot

import dev.flsrg.bot.uitls.MessageHelper.Companion.isStartMessage
import dev.flsrg.bot.uitls.MessageHelper.Companion.isThinkingMessage
import dev.flsrg.bot.repo.SQLUsersRepository
import dev.flsrg.bot.roleplay.LanguageDetector
import dev.flsrg.bot.roleplay.LanguageDetector.Language.RU
import dev.flsrg.bot.roleplay.RoleConfig
import dev.flsrg.bot.roleplay.RoleDetector
import dev.flsrg.bot.uitls.AdminHelper
import dev.flsrg.bot.uitls.BotUtils
import dev.flsrg.bot.uitls.BotUtils.KeyboardMarkupClearHistory
import dev.flsrg.bot.uitls.BotUtils.KeyboardMarkupStop
import dev.flsrg.bot.uitls.BotUtils.botMessage
import dev.flsrg.bot.uitls.BotUtils.sendTypingAction
import dev.flsrg.bot.uitls.BotUtils.withRetry
import dev.flsrg.bot.uitls.CallbackHelper
import dev.flsrg.bot.uitls.MessageHelper
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
import org.telegram.telegrambots.meta.api.objects.Update
import java.util.concurrent.ConcurrentHashMap

@OptIn(FlowPreview::class)
class Bot(botToken: String?, adminUserId: Long) : TelegramLongPollingBot(botToken) {
    companion object {
        private const val JOB_CLEANUP_INTERVAL = 5 * 60 * 1000L
    }

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val apiKey = System.getenv("OPENROUTER_API_KEY")!!
    val client = OpenRouterClient(OpenRouterConfig(apiKey = apiKey))

    private val messageHelper = MessageHelper(this)
    private val adminHelper = AdminHelper(this, adminUserId, SQLUsersRepository())
    private val roleDetector = RoleDetector(RoleConfig.allRoles)
    private val callbackHelper = CallbackHelper(this)

    private val rootScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rateLimits = ConcurrentHashMap<String, Long>()
    private val lastUsedLanguage = ConcurrentHashMap<String, LanguageDetector.Language>()
    val chatJobs = ConcurrentHashMap<String, Job>()

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
                    isStartMessage(message) -> messageHelper.sendStartMessage(chatId, RU)
                    else -> handleMessage(isThinkingMessage(message), update)
                }
            } else if (update.hasCallbackQuery()) {
                val chatId = update.callbackQuery.message.chatId.toString()
                callbackHelper.handleCallbackQuery(update, lastUsedLanguage[chatId] ?: RU)
            }
        }
    }

    private fun handleMessage(isThinking: Boolean, update: Update) {
        val startMillis = System.currentTimeMillis()
        var contentSize = 0

        val userId = update.message.from.id
        val chatId = update.message.chat.id.toString()
        val userName = update.message.from.userName
        val userMessage = update.message.text
        val lang = LanguageDetector.detectLanguage(userMessage)
        lastUsedLanguage[chatId] = lang

        if (startMillis - rateLimits.getOrDefault(chatId, 0) < BotConfig.MESSAGE_RATE_LIMIT) {
            messageHelper.sendRateLimitMessage(chatId, lang)
            return
        }
        rateLimits[chatId] = startMillis

        chatJobs[chatId]?.cancel(BotUtils.NewMessageStopException())

        val newJob = rootScope.launch {
            sendTypingAction(chatId)
            messageHelper.sendRespondingMessage(chatId, isThinking, lang)

            val messageProcessor = MessageProcessor(this@Bot, chatId)
            log.info("Responding (${if (isThinking) "R1" else "V3"}) to ${update.message.from.userName}")

            try {
                withRetry(origin = "job askDeepseekR1") {
                    messageProcessor.deleteAllReasoningMessages()
                    messageProcessor.clear()
                    adminHelper.updateUserMessage(userId, userName, OpenRouterClient.ChatMessage(role = "user", content = userMessage))

                    val finalAssistantMessage = askDeepseek(chatId, isThinking, userMessage, messageProcessor, lang)
                    adminHelper.updateUserMessage(userId, userName, finalAssistantMessage)
                }
            } catch (e: Exception) {
                val errorMessage = BotUtils.errorToMessage(e, lang)
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
        log.info("Role detected: ${role.roleName} for $language")

        client.askChat(
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
                KeyboardMarkupClearHistory(language)
            )
            finalAssistantMessage = OpenRouterClient.ChatMessage(
                role = "assistant" ,
                content = messageProcessor.getFinalAssistantMessage()
            )
        }
        .collect {
            sendTypingAction(chatId)
            messageProcessor.updateOrSend(
                KeyboardMarkupStop(language),
                KeyboardMarkupClearHistory(language)
            )
        }

        return finalAssistantMessage!!
    }

    private fun messageIsEmpty(message: OpenRouterClient.ChatResponse): Boolean {
        return message.choices.firstOrNull()?.delta?.reasoning?.isEmpty() != false
                && message.choices.firstOrNull()?.delta?.content?.isEmpty() != false
    }
}