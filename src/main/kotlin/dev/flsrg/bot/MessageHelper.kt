package dev.flsrg.bot

import dev.flsrg.bot.uitls.BotUtils
import dev.flsrg.bot.uitls.BotUtils.botMessage
import dev.flsrg.bot.uitls.BotUtils.decapitalizeFirstChar
import dev.flsrg.bot.uitls.BotUtils.editMessage
import org.telegram.telegrambots.meta.api.objects.Message
import java.util.concurrent.ConcurrentHashMap

class MessageHelper(private val bot: Bot) {
    companion object {
        private const val START_DEFAULT_COMMAND = "/start"

        private const val THINKING_MESSAGE = "Думаю..."
        private const val RESPONSE_MESSAGE = "Так, ну смотри"

        private val THINKING_PREFIX = listOf("Подумай", "Думай", "Think")
        private const val THINKING_PREFIX_RANGE = 20

        fun isStartMessage(message: Message): Boolean {
            return message.text == START_DEFAULT_COMMAND
        }

        fun isThinkingMessage(message: Message): Boolean {
            val messageRange = message.text
                .take(THINKING_PREFIX_RANGE)
                .decapitalizeFirstChar()

            return THINKING_PREFIX.any { prefix ->
                messageRange.contains(
                    prefix.decapitalizeFirstChar()
                )
            }
        }
    }

    private val messages = ConcurrentHashMap<String, Pair<Int, String>>()

    fun sendRespondingMessage(chatId: String, isThinking: Boolean) = bot.apply {
        val message = if(isThinking) THINKING_MESSAGE else RESPONSE_MESSAGE
        val messageId = execute(
            botMessage(
                chatId = chatId,
                message = message,
                buttons = listOf(BotUtils.KeyboardMarkupStop())
            )
        ).messageId

        messages[chatId] = messageId to message
    }

    fun cleanupRespondingMessageButtons(chatId: String) = bot.apply {
        if (messages.containsKey(chatId)) {
            val message = messages[chatId]!!
            execute(
                editMessage(
                    chatId = chatId,
                    messageId = message.first,
                    message = message.second,
                    buttons = null
                )
            )
            messages.remove(chatId)
        }
    }

    fun sendRateLimitMessage(chatId: String) = bot.apply {
        execute(botMessage(
            chatId = chatId,
            message = "Превышен лимит запросов. Подожди пока")
        )
    }
}