package dev.flsrg.bot.uitls

import dev.flsrg.bot.BotConfig
import dev.flsrg.bot.BotHandler
import dev.flsrg.bot.roleplay.LanguageDetector
import dev.flsrg.bot.uitls.BotUtils.botMessage
import dev.flsrg.bot.uitls.BotUtils.editMessage
import dev.flsrg.bot.uitls.BotUtils.withRetry
import dev.flsrg.llmpollingclient.model.ChatResponse
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessages
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException

class MessageProcessor(
    private val botConfig: BotConfig,
    private val botHandler: BotHandler,
    private val chatId: String,
) {
    companion object {
        private const val MARKDOWN_ERROR_MESSAGE = "can't parse entities"
        private const val MAX_MESSAGE_SKIPPED_TIMES = 2
    }

    private val contentBuffer = StringBuilder()
    private val reasoningBuffer = StringBuilder()
    private var contentMessageId: Int? = null
    private val reasoningMessageIds = linkedSetOf<Int?>()
    private var finalAssistantMessage = StringBuilder()

    private var messageSkippedTimes = 0

    suspend fun processMessage(message: ChatResponse) {
        message.choices.firstOrNull()?.delta?.let { delta ->
            delta.reasoning?.let { handleReasoning(it) }
            delta.content?.let { handleContent(it) }
        }
    }

    private suspend fun handleReasoning(reasoning: String) {
        reasoningBuffer.append(reasoning)

        if (reasoningBuffer.length > botConfig.messageMaxLength) {
            sendReasoning(isLastMessage = true)
        }
    }

    private suspend fun handleContent(content: String) {
        finalAssistantMessage.append(content)
        contentBuffer.append(content)

        if (contentBuffer.length > botConfig.messageMaxLength) {
            sendContent(isLastMessage = true, skipIfSendFailure = true)
        }
    }

    suspend fun updateOrSend(vararg buttons: BotUtils.KeyboardButton, language: LanguageDetector.Language) {
        when {
            contentBuffer.isNotEmpty() -> {
                clearReasoning(language)
                sendContent(buttons.toList())
            }
            reasoningBuffer.isNotEmpty() -> sendReasoning(buttons.toList())
        }
    }

    private fun clearReasoning(language: LanguageDetector.Language) {
        reasoningBuffer.clear()
        if (reasoningMessageIds.isNotEmpty()) {
            deleteAllReasoningMessages()
            reasoningMessageIds.clear()
            botHandler.onExecute(botMessage(chatId, Strings.ThinkingCompletedMessage.get(language)))
        }
    }

    private suspend fun sendReasoning(
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

    private suspend fun sendContent(
        buttons: List<BotUtils.KeyboardButton> = emptyList(),
        isNeedFormatting: Boolean = true,
        skipIfSendFailure: Boolean = false,
        isLastMessage: Boolean = false,
    ) {
        try {
            val contentMessage = contentBuffer.toString()

            contentMessageId = updateOrSendMessage(
                message = contentMessage,
                existingMessageId = contentMessageId,
                keyboardButtons = buttons,
                parseMode = if (isNeedFormatting) botConfig.botMessageParseMode else null
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
    private suspend fun updateOrSendMessage(
        message: String,
        existingMessageId: Int?,
        parseMode: String? = botConfig.botMessageParseMode,
        keyboardButtons: List<BotUtils.KeyboardButton> = emptyList(),
    ): Int? {
        if (message.isEmpty()) return existingMessageId
        if (message.length == prevMessage?.length) return existingMessageId

        val messageId = withRetry(maxRetries = 5, initialDelay = 5000, origin = "execute updateOrSendMessage") {
            if (existingMessageId == null) {
                val newMessage = botMessage(
                    chatId = chatId,
                    message = message,
                    buttons = keyboardButtons,
                    parseMode = parseMode,
                )

                return@withRetry botHandler.onExecute(newMessage).messageId

            } else {
                val editMessage = editMessage(
                    chatId = chatId,
                    messageId = existingMessageId,
                    message = message,
                    buttons = keyboardButtons,
                    parseMode = parseMode
                )

                botHandler.onExecute(editMessage)
                return@withRetry existingMessageId
            }
        }

        prevMessage = message
        return messageId
    }

    fun deleteAllReasoningMessages() {
        reasoningMessageIds.mapNotNull { it }.takeIf { it.isNotEmpty() }?.let { ids ->
            botHandler.onExecute(
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

    fun getFinalAssistantMessage(): String = finalAssistantMessage.toString()
}