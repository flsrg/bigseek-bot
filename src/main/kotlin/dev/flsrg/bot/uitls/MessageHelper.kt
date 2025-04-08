package dev.flsrg.bot.uitls

import dev.flsrg.bot.Bot
import dev.flsrg.bot.roleplay.LanguageDetector
import dev.flsrg.bot.uitls.BotUtils.botMessage
import dev.flsrg.bot.uitls.BotUtils.decapitalizeFirstChar
import dev.flsrg.bot.uitls.BotUtils.editMessage
import org.telegram.telegrambots.meta.api.objects.Message
import java.util.concurrent.ConcurrentHashMap

class MessageHelper(private val bot: Bot) {
    companion object {
        private const val START_DEFAULT_COMMAND = "/start"

        val RU_THINKING_PREFIX = listOf("Подумай", "Думай")
        val EN_THINKING_PREFIX = listOf("Think")
        private val THINKING_PREFIX = RU_THINKING_PREFIX + EN_THINKING_PREFIX
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

    fun sendStartMessage(chatId: String, language: LanguageDetector.Language) = bot.apply {
        execute(botMessage(chatId, Strings.StartMessage.get(language)))
    }

    fun sendRespondingMessage(chatId: String, isThinking: Boolean, language: LanguageDetector.Language) = bot.apply {
        val message = if(isThinking) {
            Strings.ThinkingMessage.get(language)
        } else {
            Strings.ResponseMessage.get(language)
        }

        val messageId = execute(
            botMessage(
                chatId = chatId,
                message = message,
                buttons = listOf(BotUtils.KeyboardButtonStop(language))
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

    fun sendRateLimitMessage(chatId: String, language: LanguageDetector.Language) = bot.apply {
        execute(
            botMessage(
                chatId = chatId,
                message = Strings.RateLimitMessage.get(language)
            )
        )
    }
}