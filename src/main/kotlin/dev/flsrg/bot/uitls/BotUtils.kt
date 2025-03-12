package dev.flsrg.bot.uitls

import dev.flsrg.bot.Bot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton

object BotUtils {
    private const val CALLBACK_DATA_FORCE_STOP = "FORCESTOP"
    private const val CALLBACK_DATA_CLEAR_HISTORY = "CLEARHISTORY"

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

    sealed class ControlKeyboardButton(val callback: String): InlineKeyboardButton()
    class KeyboardMarkupStop(): ControlKeyboardButton(CALLBACK_DATA_FORCE_STOP) {
        init {
            text = "🚫 Остановись"
            callbackData = callback
        }
    }
    class KeyboardMarkupClearHistory(): ControlKeyboardButton(CALLBACK_DATA_CLEAR_HISTORY) {
        init {
            text = "🧹 Забудь все"
            callbackData = callback
        }
    }
}