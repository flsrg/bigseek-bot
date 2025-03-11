package dev.flsrg.bot.uitls

import dev.flsrg.bot.Bot
import dev.flsrg.bot.BotConfig
import dev.flsrg.bot.uitls.BotUtils.botMessage
import dev.flsrg.bot.uitls.BotUtils.editMessage
import dev.flsrg.llmpollingclient.model.ChatResponse
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException

class MessageProcessor(
    private val bot: Bot,
    private val chatId: String,
    private val inlineKeyboardMarkup: InlineKeyboardMarkup
) {
    companion object {
        private const val MARKDOWN_PARSE_ERROR_MESSAGE = "can't parse entities: "
        private const val MESSAGE_THE_SAME_ERROR_MESSAGE = "message is not modified: specified new message content and reply markup are exactly the same as a current content and reply markup of the message"
    }

    private val log = LoggerFactory.getLogger(javaClass)

    val contentBuffer = StringBuilder()
    val reasoningBuffer = StringBuilder()
    var contentMessageId: Int? = null
    var reasoningMessageId: Int? = null

    fun processMessage(message: ChatResponse) = bot.apply {
        try {
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
                    existingMessageId = reasoningMessageId,
                    parseMode = null
                )
                reasoningBuffer.clear()
                reasoningMessageId = null
            }

            if (contentBuffer.length > BotConfig.MESSAGE_MAX_LENGTH) {
                updateOrSendMessage(
                    message = contentBuffer.toString(),
                    existingMessageId = contentMessageId
                )
                contentBuffer.clear()
                contentMessageId = null
            }

        } catch (exception: Exception) {
            log.error("error: ${exception.message}" +
                    "\n Response (reasoning = $reasoningBuffer, content = $contentBuffer)")
            throw exception
        }
    }

    fun updateOrSend() = bot.apply {
        if (contentBuffer.isNotEmpty()) {
            contentMessageId = updateOrSendMessage(
                message = contentBuffer.toString(),
                existingMessageId = contentMessageId,
                inlineKeyboardMarkup = inlineKeyboardMarkup,
            )

            reasoningBuffer.clear()

        } else if (reasoningBuffer.isNotEmpty()) {
            reasoningMessageId = updateOrSendMessage(
                message = reasoningBuffer.toString(),
                existingMessageId = reasoningMessageId,
                parseMode = null,
                inlineKeyboardMarkup = inlineKeyboardMarkup,
            )
        }
    }

    /**
     * @return existing active editable message id
     */
    private fun Bot.updateOrSendMessage(
        message: String,
        existingMessageId: Int?,
        parseMode: String? = "Markdown",
        inlineKeyboardMarkup: InlineKeyboardMarkup? = null,
    ): Int? {
        if (message.isEmpty()) return existingMessageId

        try {
            if (existingMessageId == null) {
                val newMessage = botMessage(chatId, message, parseMode)
                return execute(newMessage).messageId

            } else {
                val editMessage = editMessage(
                    chatId = chatId,
                    messageId = existingMessageId,
                    message = message,
                    keyboardMarkup = inlineKeyboardMarkup,
                    parseMode = parseMode
                )

                execute(editMessage)
                return existingMessageId
            }

        } catch (exception: TelegramApiRequestException) {
            return if (exception.message?.contains(MARKDOWN_PARSE_ERROR_MESSAGE) == true) {
                // Retry without markdown
                log.info("Markdown parse error: $exception")
                updateOrSendMessage(message, existingMessageId, null, inlineKeyboardMarkup)
            } else if (exception.message?.contains(MESSAGE_THE_SAME_ERROR_MESSAGE) == true) {
                existingMessageId
            } else throw exception
        }
    }
}