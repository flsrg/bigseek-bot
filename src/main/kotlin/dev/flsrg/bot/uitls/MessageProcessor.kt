package dev.flsrg.bot.uitls

import dev.flsrg.bot.Bot
import dev.flsrg.bot.BotConfig
import dev.flsrg.bot.uitls.BotUtils.botMessage
import dev.flsrg.bot.uitls.BotUtils.editMessage
import dev.flsrg.bot.uitls.BotUtils.withRetry
import dev.flsrg.llmpollingclient.client.OpenRouterClient.ChatResponse
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessages
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException

class MessageProcessor(private val bot: Bot, private val chatId: String) {
    private val log = LoggerFactory.getLogger(javaClass)!!

    private val contentBuffer = StringBuilder()
    private val reasoningBuffer = StringBuilder()
    private var contentMessageId: Int? = null
    private val reasoningMessageIds = linkedSetOf<Int?>()

    suspend fun processMessage(message: ChatResponse) = bot.apply {
        val delta = message.choices.firstOrNull()?.delta ?: return@apply

        val isReasoning = !delta.reasoning.isNullOrEmpty()
        val isResponding = !delta.content.isNullOrEmpty()
        if (!isReasoning && !isResponding) return@apply

        if (isReasoning) {
            reasoningBuffer.append(delta.reasoning!!)
        } else {
            contentBuffer.append(delta.content!!)
        }

        if (reasoningBuffer.length > BotConfig.MESSAGE_MAX_LENGTH) {
            updateOrSendMessage(
                message = reasoningBuffer.toString(),
                existingMessageId = reasoningMessageIds.lastOrNull(),
                parseMode = null
            )
            reasoningBuffer.clear()
            reasoningMessageIds.remove(null)
            reasoningMessageIds.add(null)
        }

        if (contentBuffer.length > BotConfig.MESSAGE_MAX_LENGTH) {
            try {
                val contentMessage = MarkdownHelper.formatMessage(contentBuffer.toString())
                updateOrSendMessage(
                    message = contentMessage,
                    existingMessageId = contentMessageId
                )
            } catch (e: TelegramApiRequestException) {
                if (e.errorCode != BotConfig.BAD_REQUEST_ERROR_CODE) throw e
                log.debug("Can't send message: ${e.message}, skip")
                // just skip sending the message
            }

            contentBuffer.clear()
            contentMessageId = null
        }
    }

    suspend fun updateOrSend(vararg buttons: BotUtils.ControlKeyboardButton) = bot.apply {
        if (contentBuffer.isNotEmpty()) {
            reasoningBuffer.clear()
            if (reasoningMessageIds.isNotEmpty()) {
                deleteAllReasoningMessages(reasoningMessageIds)
                reasoningMessageIds.clear()
                execute(botMessage(chatId, "Подумал, получается:"))
            }

            val contentMessage = MarkdownHelper.formatMessage(contentBuffer.toString())
            try {
                contentMessageId = updateOrSendMessage(
                    message = contentMessage,
                    existingMessageId = contentMessageId,
                    keyboardButtons = buttons,
                )
            } catch (e: TelegramApiRequestException) {
                if (e.errorCode == BotConfig.BAD_REQUEST_ERROR_CODE) {
                    contentMessageId = updateOrSendMessage(
                        message = contentMessage,
                        existingMessageId = contentMessageId,
                        keyboardButtons = buttons,
                        parseMode = null,
                    )
                    log.debug("Can't send message: ${e.message}, send without formatting")
                }
            }

        } else if (reasoningBuffer.isNotEmpty()) {
            val reasoningMessageId = updateOrSendMessage(
                message = reasoningBuffer.toString(),
                existingMessageId = reasoningMessageIds.lastOrNull(),
                parseMode = null,
                keyboardButtons = buttons,
            )
            reasoningMessageIds.add(reasoningMessageId)
        }
        log.info("reasoningMessageIds: $reasoningMessageIds")
    }

    /**
     * @return existing active editable message id
     */
    private suspend fun Bot.updateOrSendMessage(
        message: String,
        existingMessageId: Int?,
        parseMode: String? = "MarkdownV2",
        vararg keyboardButtons: BotUtils.ControlKeyboardButton,
    ): Int? {
        if (message.isEmpty()) return existingMessageId

        return withRetry(maxRetries = 5, initialDelay = 5000, origin = "execute updateOrSendMessage") {
            if (existingMessageId == null) {
                val newMessage = botMessage(chatId, message, parseMode)
                execute(newMessage).messageId

            } else {
                val editMessage = editMessage(
                    chatId = chatId,
                    messageId = existingMessageId,
                    message = message,
                    keyboardMarkup = createControlKeyboard(keyboardButtons.toList()),
                    parseMode = parseMode
                )

                execute(editMessage)
                existingMessageId
            }
        }
    }

    private fun createControlKeyboard(buttons: List<BotUtils.ControlKeyboardButton>): InlineKeyboardMarkup {
        return InlineKeyboardMarkup.builder()
            .keyboard(listOf(buttons))
            .build()
    }

    private fun deleteAllReasoningMessages(reasoningMessageIds: Set<Int?>) {
        bot.execute(
            DeleteMessages.builder()
                .chatId(chatId)
                .messageIds(reasoningMessageIds.mapNotNull { it })
                .build()
        )
    }

    fun deleteAllReasoningMessages(bot: Bot) {
        reasoningMessageIds.mapNotNull { it }.let {
            if (it.isNotEmpty()) {
                deleteAllReasoningMessages(reasoningMessageIds)
                bot.execute(bot.botMessage(chatId, "Ща, по новой..."))
            }
        }
    }
}