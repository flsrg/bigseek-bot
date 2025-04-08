package dev.flsrg.bot

import dev.flsrg.bot.db.Database
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

private val log = LoggerFactory.getLogger("Main")

fun main() {
    val token = System.getenv("BIG_SEEK_BOT_TOKEN")!!
    val adminUserId = System.getenv("BIG_SEEK_BOT_ADMIN_USER_ID")!!.toLong()

    Database.init(Bot.BOT_USER_NAME)

    val bot = Bot(token, adminUserId)
    TelegramBotsApi(DefaultBotSession::class.java)
        .registerBot(bot)

    log.info("Bot started (version: ${BotConfig.getBotVersion()})")
}