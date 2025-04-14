package dev.flsrg.bot

import org.telegram.telegrambots.meta.api.methods.BotApiMethod
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.io.Serializable

interface BotHandler {
    @Throws(TelegramApiException::class)
    fun <T : Serializable?, Method : BotApiMethod<T>?> onExecute(method: Method): T
}