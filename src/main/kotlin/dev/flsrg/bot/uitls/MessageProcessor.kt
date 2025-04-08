package dev.flsrg.bot.uitls

import dev.flsrg.bot.Bot
import dev.flsrg.bot.BotConfig
import dev.flsrg.bot.uitls.BotUtils.botMessage
import dev.flsrg.bot.uitls.BotUtils.editMessage
import dev.flsrg.bot.uitls.BotUtils.withRetry
import dev.flsrg.llmpollingclient.client.OpenRouterClient.ChatResponse
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessages
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException

class MessageProcessor(private val bot: Bot, private val chatId: String) {
    companion object {
        private const val MARKDOWN_ERROR_MESSAGE = "can't parse entities"
        private const val MAX_MESSAGE_SKIPPED_TIMES = 2
    }

    private val contentBuffer = StringBuilder()
    private val reasoningBuffer = StringBuilder()
    private var contentMessageId: Int? = null
    private val reasoningMessageIds = linkedSetOf<Int?>()
    private var finalAssistantMessage = ""

    private var messageSkippedTimes = 0

    suspend fun processMessage(message: ChatResponse) = bot.apply {
        message.choices.firstOrNull()?.delta?.let { delta ->
            delta.reasoning?.let { handleReasoning(it) }
            delta.content?.let { handleContent(it) }
        }
    }

    private suspend fun Bot.handleReasoning(reasoning: String) {
        reasoningBuffer.append(reasoning)

        if (reasoningBuffer.length > BotConfig.MESSAGE_MAX_LENGTH) {
            sendReasoning(isLastMessage = true)
        }
    }

    private suspend fun Bot.handleContent(content: String) {
        contentBuffer.append(content)

        if (contentBuffer.length > BotConfig.MESSAGE_MAX_LENGTH) {
            sendContent(isLastMessage = true, skipIfSendFailure = true)
        }
    }

    suspend fun updateOrSend(vararg buttons: BotUtils.KeyboardButton) = bot.apply {
        when {
            contentBuffer.isNotEmpty() -> {
                clearReasoning()
                sendContent(buttons.toList())
            }
            reasoningBuffer.isNotEmpty() -> sendReasoning(buttons.toList())
        }
    }

    private fun Bot.clearReasoning() {
        reasoningBuffer.clear()
        if (reasoningMessageIds.isNotEmpty()) {
            deleteAllReasoningMessages()
            reasoningMessageIds.clear()
            execute(botMessage(chatId, "Подумал, получается:"))
        }
    }

    private suspend fun Bot.sendReasoning(
        buttons: List<BotUtils.KeyboardButton> = emptyList(),
        isLastMessage: Boolean = false,
    ) {
        val reasoningMessageId = updateOrSendMessage(
            message = reasoningBuffer.toString(),
            existingMessageId = reasoningMessageIds.lastOrNull(),
            parseMode = null,
            keyboardButtons = buttons,
        )
        reasoningMessageIds.add(reasoningMessageId)

        if (isLastMessage) {
            reasoningBuffer.clear()
            reasoningMessageIds.remove(null)
            reasoningMessageIds.add(null)
        }
    }

    private suspend fun Bot.sendContent(
        buttons: List<BotUtils.KeyboardButton> = emptyList(),
        isNeedFormatting: Boolean = true,
        skipIfSendFailure: Boolean = false,
        isLastMessage: Boolean = false,
    ) {
        try {
            val contentMessage = contentBuffer.toString()
            finalAssistantMessage += contentMessage

            contentMessageId = updateOrSendMessage(
                message = contentMessage,
                existingMessageId = contentMessageId,
                keyboardButtons = buttons,
                parseMode = if (isNeedFormatting) BotConfig.BOT_MESSAGES_PARSE_MODE else null
            )
        } catch (e: TelegramApiRequestException) {
            if (e.errorCode == BotConfig.BAD_REQUEST_ERROR_CODE && e.message?.contains(MARKDOWN_ERROR_MESSAGE) == true) {
                if (skipIfSendFailure && messageSkippedTimes < MAX_MESSAGE_SKIPPED_TIMES) {
                    messageSkippedTimes++
                    return
                } else {
                    sendContent(buttons, isNeedFormatting = false)
                }
            }
        }

        if (isLastMessage) {
            contentBuffer.clear()
            contentMessageId = null
        }
    }

    private var prevMessage: String? = null

    /**
     * @return existing active editable message id
     */
    private suspend fun Bot.updateOrSendMessage(
        message: String,
        existingMessageId: Int?,
        parseMode: String? = BotConfig.BOT_MESSAGES_PARSE_MODE,
        keyboardButtons: List<BotUtils.KeyboardButton> = emptyList(),
    ): Int? {
        if (message.isEmpty()) return existingMessageId
        if (message == prevMessage) return existingMessageId

        return withRetry(maxRetries = 5, initialDelay = 5000, origin = "execute updateOrSendMessage") {
            if (existingMessageId == null) {
                val newMessage = botMessage(
                    chatId = chatId,
                    message = message,
                    buttons = keyboardButtons,
                    parseMode = parseMode,
                )

                execute(newMessage).messageId

            } else {
                val editMessage = editMessage(
                    chatId = chatId,
                    messageId = existingMessageId,
                    message = message,
                    buttons = keyboardButtons,
                    parseMode = parseMode
                )

                execute(editMessage)
                existingMessageId
            }
        }

        prevMessage = message
    }

    fun deleteAllReasoningMessages() {
        reasoningMessageIds.mapNotNull { it }.takeIf { it.isNotEmpty() }?.let { ids ->
            bot.execute(
                DeleteMessages.builder()
                    .chatId(chatId)
                    .messageIds(ids)
                    .build()
            )
        }
    }

    fun clear() {
        reasoningBuffer.clear()
        contentBuffer.clear()
        contentMessageId = null
        reasoningMessageIds.clear()
    }

    fun getFinalAssistantMessage(): String = finalAssistantMessage
}