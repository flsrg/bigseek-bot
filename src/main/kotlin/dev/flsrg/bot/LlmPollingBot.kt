package dev.flsrg.bot

import dev.flsrg.bot.db.Database
import dev.flsrg.bot.hist.HistoryManager
import dev.flsrg.bot.repo.SQLChatHistRepository
import dev.flsrg.bot.uitls.MessageHelper.Companion.isStartMessage
import dev.flsrg.bot.uitls.MessageHelper.Companion.isThinkingMessage
import dev.flsrg.bot.repo.SQLUsersRepository
import dev.flsrg.bot.roleplay.LanguageDetector
import dev.flsrg.bot.roleplay.LanguageDetector.Language.RU
import dev.flsrg.bot.roleplay.RoleConfig
import dev.flsrg.bot.roleplay.RoleDetector
import dev.flsrg.bot.uitls.AdminHelper
import dev.flsrg.bot.uitls.BotUtils
import dev.flsrg.bot.uitls.BotUtils.KeyboardButtonClearHistory
import dev.flsrg.bot.uitls.BotUtils.KeyboardButtonStop
import dev.flsrg.bot.uitls.BotUtils.botMessage
import dev.flsrg.bot.uitls.BotUtils.sendTypingAction
import dev.flsrg.bot.uitls.BotUtils.withRetry
import dev.flsrg.bot.uitls.CallbackHelper
import dev.flsrg.bot.uitls.MessageHelper
import dev.flsrg.bot.uitls.MessageProcessor
import dev.flsrg.llmpollingclient.client.Client
import dev.flsrg.llmpollingclient.client.OpenRouterConfig
import dev.flsrg.llmpollingclient.model.ChatMessage
import dev.flsrg.llmpollingclient.model.ChatResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.api.objects.Update
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap

@OptIn(FlowPreview::class)
class LlmPollingBot(
    botToken: String?,
    adminUserId: Long,
    private val botUsername: String,
    private val client: Client,
    private val botConfig: BotConfig,
) : TelegramLongPollingBot(botToken), BotHandler {
    private val log: Logger = LoggerFactory.getLogger(javaClass)

    private val messageHelper = MessageHelper(this)
    private val usersRepository = SQLUsersRepository()
    private val adminHelper = AdminHelper(this, adminUserId, usersRepository)
    private val roleDetector = RoleDetector(RoleConfig.allRoles)
    private val callbackHelper = CallbackHelper(this)
    val historyManager = HistoryManager(
        botConfig = botConfig,
        histRepository = SQLChatHistRepository(Database.database),
        usersRepository = usersRepository,
    )

    private val rootScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val rateLimits = ConcurrentHashMap<String, Long>()
    private val lastUsedLanguage = ConcurrentHashMap<String, LanguageDetector.Language>()
    val chatJobs = ConcurrentHashMap<String, Job>()

    // Cleanup mechanism to remove completed jobs
    init {
        rootScope.launch {
            while (isActive) {
                delay(botConfig.jobCleanupInterval)
                chatJobs.entries.removeAll { (_, job) -> job.isCompleted }
            }
        }
        Database.init(botUsername)
    }

    override fun getBotUsername() = botUsername

    override fun <T : Serializable?, Method : BotApiMethod<T>?> onExecute(method: Method): T = execute(method)

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
                when {
                    adminHelper.isAdminCallback(update) -> adminHelper.handleCallbackQuery(update)
                    else -> callbackHelper.handleCallbackQuery(update, lastUsedLanguage[chatId] ?: RU)
                }
            }
        }
    }

    private fun handleMessage(isThinking: Boolean, update: Update) {
        val startMillis = System.currentTimeMillis()

        val userId = update.message.from.id
        val chatId = update.message.chat.id.toString()
        val userName = update.message.from.userName
        val userMessage = update.message.text
        val lang = LanguageDetector.detectLanguage(userMessage)
        lastUsedLanguage[chatId] = lang

        if (startMillis - rateLimits.getOrDefault(chatId, 0) < botConfig.messageRateLimit) {
            messageHelper.sendRateLimitMessage(chatId, lang)
            return
        }
        rateLimits[chatId] = startMillis

        chatJobs[chatId]?.cancel(BotUtils.NewMessageStopException())

        val newJob = rootScope.launch {
            sendTypingAction(chatId)
            messageHelper.sendRespondingMessage(chatId, isThinking, lang)

            val messageProcessor = MessageProcessor(
                botConfig = botConfig,
                botHandler = this@LlmPollingBot,
                chatId = chatId,
            )
            log.info("Responding (${if (isThinking) "R1" else "V3"}) to ${update.message.from.userName}")

            try {
                withRetry(origin = "job askDeepseekR1") {
                    messageProcessor.deleteAllReasoningMessages()
                    messageProcessor.clear()
                    adminHelper.updateUserMessage(userId, userName)

                    askDeepseek(userId, chatId, isThinking, userMessage, messageProcessor, lang)
                    adminHelper.updateUserMessage(userId, userName)
                }
            } catch (e: Exception) {
                val errorMessage = BotUtils.errorToMessage(e, lang)
                onExecute(botMessage(chatId, errorMessage))

                log.error("Error processing message", e)

            } finally {
                chatJobs.remove(chatId)
                log.info("Responding to ${update.message.from.userName} completed " +
                        "(${System.currentTimeMillis() - startMillis}ms)")
            }
        }

        chatJobs[chatId] = newJob
    }

    private suspend fun askDeepseek(
        userId: Long,
        chatId: String,
        isThinking: Boolean,
        userMessage: String,
        messageProcessor: MessageProcessor,
        language: LanguageDetector.Language,
    ) {
        val model = if (isThinking) OpenRouterConfig.DEEPSEEK_R1 else OpenRouterConfig.DEEPSEEK_V3
        var finalAssistantMessage: ChatMessage? = null
        val role = roleDetector.detectRole(userMessage, language)
        log.info("Role detected: ${role.roleName} for $language")

        historyManager.addMessage(userId, ChatMessage(role = "user", content = userMessage))
        val messages = historyManager.getHistory(userId)

        val systemMessage = if (language == RU) {
            role.russianSystemMessage!!
        } else {
            role.systemMessage
        }.let {
            ChatMessage(role = "system", content = it)
        }

        client.askChat(
            model = model,
            messages = messages,
            systemMessage = systemMessage,
        ).onEach { message ->
            if (!messageIsEmpty(message)) messageHelper.cleanupRespondingMessageButtons(chatId)
            messageProcessor.processMessage(message)
        }
        .sample(botConfig.messageSamplingDuration)
        .onCompletion { exception ->
            if (exception != null) throw exception

            messageProcessor.updateOrSend(KeyboardButtonClearHistory(language), language = language)
            finalAssistantMessage = ChatMessage(
                role = "assistant" ,
                content = messageProcessor.getFinalAssistantMessage()
            )
        }
        .collect {
            sendTypingAction(chatId)
            messageProcessor.updateOrSend(
                KeyboardButtonStop(language),
                KeyboardButtonClearHistory(language),
                language = language,
            )
        }

        if (finalAssistantMessage?.content?.isEmpty() != false) {
            throw BotUtils.ExceptionEmptyResponse()
        }

        if (finalAssistantMessage != null) {
            historyManager.addMessage(userId, finalAssistantMessage!!)
        }
    }

    private fun messageIsEmpty(message: ChatResponse): Boolean {
        return message.choices.firstOrNull()?.delta?.reasoning?.isEmpty() != false
                && message.choices.firstOrNull()?.delta?.content?.isEmpty() != false
    }
}