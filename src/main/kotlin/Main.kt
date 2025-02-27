package dev.flsrg

import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

fun main() {
    // TODO: Replace it to the app.properties file
    val TOKEN = "7531676551:AAFpOkvi_iSIMNMRrzNOPrK_0OdaVg58Pro"

    TelegramBotsApi(DefaultBotSession::class.java)
        .registerBot(Bot(TOKEN))
}