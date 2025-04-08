package dev.flsrg.bot.uitls

import dev.flsrg.bot.Bot
import dev.flsrg.bot.BotConfig
import dev.flsrg.bot.roleplay.LanguageDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.api.methods.ActionType
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException

object BotUtils {
    private val log = LoggerFactory.getLogger(javaClass)

    fun Bot.editMessage(
        chatId: String, messageId: Int,
        message: String,
        buttons: List<KeyboardButton>? = null,
        parseMode: String? = null,
    ): EditMessageText {
        return EditMessageText.builder()
            .chatId(chatId)
            .messageId(messageId)
            .text(message)
            .parseMode(parseMode)
            .replyMarkup(buttons?.let { createInlineKeyboardMarkup(it) })
            .build()
    }

    fun Bot.botMessage(
        chatId: String,
        message: String,
        buttons: List<KeyboardButton>? = null,
        parseMode: String? = null
    ): SendMessage {
        return SendMessage.builder()
            .chatId(chatId)
            .text(message)
            .parseMode(parseMode)
            .replyMarkup(buttons?.let { createInlineKeyboardMarkup(it) })
            .build()
    }

    sealed class KeyboardButton(buttonText: String, callback: String?): InlineKeyboardButton() {
        init {
            text = buttonText
            callbackData = callback
        }
    }

    class KeyboardButtonStop(language: LanguageDetector.Language): KeyboardButton(
        Strings.KeyboardStopText.get(language),
        CallbackHelper.CALLBACK_DATA_FORCE_STOP
    )
    class KeyboardButtonClearHistory(language: LanguageDetector.Language): KeyboardButton(
        Strings.KeyboardClearHistoryText.get(language),
        CallbackHelper.CALLBACK_DATA_CLEAR_HISTORY
    )

    fun createInlineKeyboardMarkup(buttons: List<KeyboardButton>): InlineKeyboardMarkup {
        return InlineKeyboardMarkup.builder()
            .keyboard(listOf(buttons))
            .build()
    }

    suspend fun <T> withRetry(
        maxRetries: Int = 3,
        initialDelay: Long = 1000,
        maxDelay: Long = 10000,
        origin: String? = getCallerMethodName(),
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                if (attempt == maxRetries - 1 || !isRetryable(e)) throw e // Fail on final attempt
                log.debug("Retrying in $origin after $currentDelay ms due to: $e")

                delay(currentDelay + (Math.random() * 1000).toLong()) // Add jitter
                currentDelay = minOf((currentDelay * 2), maxDelay) // Exponential backoff
            }
        }
        throw RetryFailedException("Max retries ($maxRetries) exceeded")
    }

    private fun isRetryable(e: Exception): Boolean {
        return when (e) {
            is TelegramApiRequestException -> {
                when (e.errorCode) {
                    BotConfig.RATE_LIMIT_ERROR_CODE -> true
                    BotConfig.BAD_REQUEST_ERROR_CODE -> e.message?.contains("message to edit not found") == true
                    else -> false
                }
            }
            is ExceptionEmptyResponse -> true
            else -> false
        }
    }

    class ExceptionEmptyResponse() : Exception("Empty response")

    fun getCallerMethodName(): String? {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk { frames ->
            frames.skip(1).findFirst().map { it.methodName }.orElse(null)
        }
    }

    fun errorToMessage(exception: Exception, language: LanguageDetector.Language): String {
        return if (exception is CancellationException) {
            when (exception) {
                is UserStoppedException -> return Strings.StopErrorUser.get(language)
                is NewMessageStopException -> return Strings.StopErrorUser.get(language)
                else -> "error: ${exception.message}"
            }
        } else {
            "error: ${exception.message}"
        }
    }

    class UserStoppedException: CancellationException("User requested stop")
    class NewMessageStopException: CancellationException("New message in chat")

    fun Bot.sendTypingAction(chatId: String) {
        execute(
            SendChatAction.builder()
                .chatId(chatId)
                .action(ActionType.TYPING.name)
                .build()
        )
    }

    fun String.decapitalizeFirstChar(): String = replaceFirstChar { it.lowercase() }
}

class RetryFailedException(message: String) : Exception(message)