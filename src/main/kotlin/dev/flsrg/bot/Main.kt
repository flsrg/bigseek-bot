package dev.flsrg.bot

import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

private val log = LoggerFactory.getLogger("Main")

fun main() {
    // TODO: Replace it to the System env variable
    val TOKEN = "7531676551:AAFpOkvi_iSIMNMRrzNOPrK_0OdaVg58Pro"

    TelegramBotsApi(DefaultBotSession::class.java)
        .registerBot(Bot(TOKEN))

    log.info("Bot started (version: ${BotConfig.getBotVersion()})")
}