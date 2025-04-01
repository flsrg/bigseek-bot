package dev.flsrg.bot

import dev.flsrg.bot.uitls.BotUtils
import dev.flsrg.bot.uitls.BotUtils.botMessage
import dev.flsrg.bot.uitls.BotUtils.editMessage
import java.util.concurrent.ConcurrentHashMap

class MessageHelper(private val bot: Bot) {
    companion object {
        private const val THINKING_MESSAGE = "Думаю..."
    }

    private val messages = ConcurrentHashMap<String, Int>()

    fun sendThinkingMessage(chatId: String) = bot.apply {
        val messageId = execute(
            botMessage(
                chatId = chatId,
                message = THINKING_MESSAGE,
                buttons = listOf(BotUtils.KeyboardMarkupStop())
            )
        ).messageId

        messages[chatId] = messageId
    }

    fun cleanupThinkingMessageButtons(chatId: String) = bot.apply {
        if (messages.containsKey(chatId)) {
            execute(
                editMessage(
                    chatId = chatId,
                    messageId = messages[chatId]!!,
                    message = THINKING_MESSAGE,
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