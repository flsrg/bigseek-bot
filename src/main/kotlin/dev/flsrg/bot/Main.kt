package dev.flsrg.bot

import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

private val log = LoggerFactory.getLogger("Main")

fun main() {
    val token = System.getenv("BIG_SEEK_BOT_TOKEN")!!

    TelegramBotsApi(DefaultBotSession::class.java)
        .registerBot(Bot(token))

    log.info("Bot started (version: ${BotConfig.getBotVersion()})")
}