package dev.flsrg.bot.uitls

import dev.flsrg.bot.Bot
import dev.flsrg.bot.BotConfig
import dev.flsrg.bot.uitls.BotUtils.botMessage
import dev.flsrg.bot.uitls.BotUtils.editMessage
import dev.flsrg.llmpollingclient.model.ChatResponse
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException

class MessageProcessor {
    companion object {
        private const val MARKDOWN_PARSE_ERROR_MESSAGE = "can't parse entities: Can't find end of the entity starting at byte offset"
        private const val MESSAGE_THE_SAME_ERROR_MESSAGE = "message is not modified: specified new message content and reply markup are exactly the same as a current content and reply markup of the message"
    }

    private val log = LoggerFactory.getLogger(javaClass)

    val buffer = StringBuilder()
    val contentBuffer = StringBuilder()
    val reasoningBuffer = StringBuilder()
    val fullContentBuffer = StringBuilder()
    var contentMessageId: Int? = null
    var reasoningMessageId: Int? = null

    fun processStream(
        bot: Bot,
        chatId: String,
        chatResponse: ChatResponse,
        keyboardMarkup: InlineKeyboardMarkup
    ) = bot.apply {
        try {
            val delta = chatResponse.choices.firstOrNull()?.delta

            if (delta != null) {
                val isReasoning = !delta.reasoning.isNullOrEmpty()
                val isResponding = !delta.content.isNullOrEmpty()

                if (!isReasoning && !isResponding) return@apply

                if (isReasoning) {
                    buffer.append(delta.reasoning)
                } else {
                    buffer.append(delta.content)
                }

                val isParagraph = if (isReasoning) {
                    delta.reasoning!!.contains("\n")
                } else {
                    delta.content?.contains("\n") == true
                }

                if (isParagraph) {
                    if (isReasoning) {
                        reasoningBuffer.append(buffer.toString())
                        val reasoningMessage = reasoningBuffer.toString()

                        if (reasoningBuffer.length > BotConfig.MESSAGE_MAX_LENGTH) {
                            reasoningMessageId = null
                            reasoningBuffer.clear()
                        }

                        reasoningMessageId = updateOrSendMessage(
                            chatId = chatId,
                            message = reasoningMessage,
                            existingMessageId = reasoningMessageId,
                            keyboardMarkup = keyboardMarkup
                        )
                    } else {
                        contentBuffer.append(buffer.toString())
                        val contentMessage = contentBuffer.toString()

                        fullContentBuffer.append(contentMessage)

                        if (contentBuffer.length > BotConfig.MESSAGE_MAX_LENGTH) {
                            contentMessageId = null
                            contentBuffer.clear()
                        }

                        contentMessageId = updateOrSendMessage(
                            chatId = chatId,
                            message = contentMessage,
                            existingMessageId = contentMessageId,
                            keyboardMarkup = keyboardMarkup
                        )
                    }

                    buffer.clear()
                }
            }
        } catch (exception: Exception) {
            log.error("error: ${exception.message}" +
                    "\n Response (reasoning = $reasoningBuffer, content = $contentBuffer)")
            throw exception

        }
    }

    fun finishMessage(bot: Bot, chatId: String, keyboardMarkup: InlineKeyboardMarkup) {
        if (buffer.isNotEmpty()) {
            contentBuffer.append(buffer.toString())
            val contentMessage = contentBuffer.toString()
            fullContentBuffer.append(contentMessage)
            bot.updateOrSendMessage(chatId, contentMessage, contentMessageId, keyboardMarkup)
        }
    }

    /**
     * @return existing active editable message id
     */
    private fun Bot.updateOrSendMessage(
        chatId: String,
        message: String,
        existingMessageId: Int?,
        keyboardMarkup: InlineKeyboardMarkup,
    ): Int? {
        if (message.isEmpty()) return existingMessageId

        try {
            if (existingMessageId == null) {
                val newMessage = botMessage(chatId, message)
                return execute(newMessage).messageId
            } else {
                val editMessage = editMessage(chatId, existingMessageId, message, keyboardMarkup)
                execute(editMessage)

                return existingMessageId
            }

        } catch (exception: TelegramApiRequestException) {
            if (exception.message?.contains(MARKDOWN_PARSE_ERROR_MESSAGE) == true) {
                log.error("Markdown parse error, skip message")
                return existingMessageId
            } else if (exception.message?.contains(MESSAGE_THE_SAME_ERROR_MESSAGE) == true) {
                log.error("Message the same error, skip message")
                return existingMessageId
            }
            else throw exception
        }
    }
}