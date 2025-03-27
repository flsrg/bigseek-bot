package dev.flsrg.bot.uitls

import dev.flsrg.bot.Bot
import dev.flsrg.bot.BotConfig
import dev.flsrg.bot.uitls.BotUtils.botMessage
import dev.flsrg.bot.uitls.BotUtils.editMessage
import dev.flsrg.bot.uitls.BotUtils.withRetry
import dev.flsrg.llmpollingclient.client.OpenRouterClient.ChatResponse
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessages
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup

class MessageProcessor(private val bot: Bot, private val chatId: String) {
    val contentBuffer = StringBuilder()
    val reasoningBuffer = StringBuilder()
    val fullContent = StringBuilder()
    var contentMessageId: Int? = null
    val reasoningMessageIds = mutableSetOf<Int?>()

    suspend fun processMessage(message: ChatResponse) = bot.apply {
        val delta = message.choices.firstOrNull()?.delta ?: return@apply

        val isReasoning = !delta.reasoning.isNullOrEmpty()
        val isResponding = !delta.content.isNullOrEmpty()
        if (!isReasoning && !isResponding) return@apply

        if (isReasoning) {
            reasoningBuffer.append(delta.reasoning!!)
        } else {
            contentBuffer.append(delta.content!!)
            fullContent.append(delta.content!!)
        }

        if (reasoningBuffer.length > BotConfig.MESSAGE_MAX_LENGTH) {
            updateOrSendMessage(
                message = reasoningBuffer.toString(),
                existingMessageId = reasoningMessageIds.lastOrNull(),
                parseMode = null
            )
            reasoningBuffer.clear()
            reasoningMessageIds.add(null)
        }

        if (contentBuffer.length > BotConfig.MESSAGE_MAX_LENGTH) {
            val contentMessage = MarkdownHelper.formatMessage(contentBuffer.toString())
            updateOrSendMessage(
                message = contentMessage,
                existingMessageId = contentMessageId
            )
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
            contentMessageId = updateOrSendMessage(
                message = contentMessage,
                existingMessageId = contentMessageId,
                keyboardButtons = buttons,
            )

        } else if (reasoningBuffer.isNotEmpty()) {
            val reasoningMessageId = updateOrSendMessage(
                message = reasoningBuffer.toString(),
                existingMessageId = reasoningMessageIds.lastOrNull(),
                parseMode = null,
                keyboardButtons = buttons,
            )
            reasoningMessageIds.add(reasoningMessageId)
        }
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

        var messageId = existingMessageId
        withRetry(maxRetries = 5, initialDelay = 2000, origin = "execute updateOrSendMessage") {
            if (existingMessageId == null) {
                val newMessage = botMessage(chatId, message, parseMode)
                messageId = execute(newMessage).messageId

            } else {
                val editMessage = editMessage(
                    chatId = chatId,
                    messageId = existingMessageId,
                    message = message,
                    keyboardMarkup = createControlKeyboard(keyboardButtons.toList()),
                    parseMode = parseMode
                )


                execute(editMessage)
            }
        }

        return messageId
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
}