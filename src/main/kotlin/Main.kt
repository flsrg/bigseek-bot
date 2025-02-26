package dev.flsrg

import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

fun main() {
    // TODO: Replace it to the app.properties file
    val TOKEN = "7531676551:AAFpOkvi_iSIMNMRrzNOPrK_0OdaVg58Pro"

    val botsApi: TelegramBotsApi?
    try {
        botsApi = TelegramBotsApi(DefaultBotSession::class.java)
    } catch (e: TelegramApiException) {
        throw RuntimeException(e)
    }

    val bot = Bot(TOKEN)
    botsApi.registerBot(bot)
}