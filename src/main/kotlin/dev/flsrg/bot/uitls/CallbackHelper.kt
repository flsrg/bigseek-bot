package dev.flsrg.bot.uitls

import dev.flsrg.bot.Bot
import dev.flsrg.bot.roleplay.LanguageDetector
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

class CallbackHelper(private val bot: Bot) {
    companion object {
        private const val CALLBACK_DATA_FORCE_STOP = "FORCESTOP"
        private const val CALLBACK_DATA_CLEAR_HISTORY = "CLEARHISTORY"
    }
    
    fun handleCallbackQuery(update: Update, language: LanguageDetector.Language) {
        val callback = update.callbackQuery
        val chatId = callback.message.chatId.toString()
        val callbackId = callback.id

        when (callback.data) {
            CALLBACK_DATA_FORCE_STOP -> {
                forceStop(chatId, callbackId, language)
            }
            CALLBACK_DATA_CLEAR_HISTORY -> {
                forceStop(chatId, callbackId, language)
                clearHistory(chatId, callbackId, language)
            }
        }
    }

    private fun forceStop(chatId: String, callbackId: String, language: LanguageDetector.Language) = bot.apply {
        val job = chatJobs[chatId]

        try {
            if (job != null) {
                job.cancel(BotUtils.UserStoppedException())

                execute(
                    AnswerCallbackQuery.builder()
                        .callbackQueryId(callbackId)
                        .text(Strings.CallbackStopSuccessAnswer.get(language))
                        .build()
                )
            } else {
                execute(
                    AnswerCallbackQuery.builder()
                        .callbackQueryId(callbackId)
                        .text(Strings.CallbackStopNothingRunningAnswer.get(language))
                        .build()
                )
            }
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }

    private fun clearHistory(chatId: String, callbackId: String, language: LanguageDetector.Language) = bot.apply {
        // Clear the chat history
        client.clearHistory(chatId)

        // Send confirmation to the user
        execute(
            AnswerCallbackQuery.builder()
                .callbackQueryId(callbackId)
                .text(Strings.CallbackClearHistorySuccessAnswer.get(language))
                .build()
        )

        // Optionally, send a message to the chat confirming the history is cleared
        try {
            execute(
                SendMessage.builder()
                    .chatId(chatId)
                    .text(Strings.CallbackClearHistorySuccessMessage.get(language))
                    .build()
            )
        } catch (e: TelegramApiException) {
            e.printStackTrace()
        }
    }
}