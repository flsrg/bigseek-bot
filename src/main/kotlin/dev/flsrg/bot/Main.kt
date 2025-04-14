package dev.flsrg.bot

import dev.flsrg.llmpollingclient.client.OpenRouterClient
import dev.flsrg.llmpollingclient.client.OpenRouterConfig
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

private val log = LoggerFactory.getLogger("Main")

fun main() {
    val token = System.getenv("BIG_SEEK_BOT_TOKEN")!!
    val adminUserId = System.getenv("BIG_SEEK_BOT_ADMIN_USER_ID")!!.toLong()
    val botUserName = "BigDick"
    val apiKey = System.getenv("OPENROUTER_API_KEY")!!
    val client = OpenRouterClient(OpenRouterConfig(apiKey = apiKey))

    val bot = LlmPollingBot(
        botToken = token,
        adminUserId = adminUserId,
        botUsername = botUserName,
        client = client,
        botConfig = DefaultBotConfig(),
    )

    TelegramBotsApi(DefaultBotSession::class.java)
        .registerBot(bot)

    log.info("Bot started (version: ${BotConfig.getBotVersion()})")
}