package dev.flsrg.bot.uitls

import dev.flsrg.bot.Bot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup

object BotUtils {
    fun Bot.editMessage(
        chatId: String, messageId: Int,
        message: String,
        keyboardMarkup: InlineKeyboardMarkup? = null,
        parseMode: String? = "Markdown",
    ): EditMessageText {
        return EditMessageText.builder()
            .chatId(chatId)
            .messageId(messageId)
            .text(message)
            .parseMode(parseMode)
            .replyMarkup(keyboardMarkup)
            .build()
    }

    fun Bot.botMessage(chatId: String, message: String, parseMode: String? = "Markdown"): SendMessage {
        return SendMessage.builder()
            .chatId(chatId)
            .text(message)
            .parseMode(parseMode)
            .build()
    }
}