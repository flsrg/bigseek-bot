package dev.flsrg.bot

import org.telegram.telegrambots.meta.api.methods.ParseMode
import java.util.Properties
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

abstract class BotConfig {
    companion object {
        const val RATE_LIMIT_ERROR_CODE = 429
        const val BAD_REQUEST_ERROR_CODE = 400

        fun getBotVersion(): String {
            val props = Properties()
            props.load(this::class.java.classLoader.getResourceAsStream("version.properties"))
            return props.getProperty("bot.version")
        }
    }

    abstract val messageMaxLength: Int
    abstract val messageSamplingDuration: Long
    abstract val messageRateLimit: Long
    abstract val maxHistorySize: Int
    abstract val jobCleanupInterval: Long
    abstract val botMessageParseMode: String
}

class DefaultBotConfig : BotConfig() {
    override val messageMaxLength = 2048
    override val messageSamplingDuration = 1.seconds.inWholeMilliseconds
    override val messageRateLimit = 5.seconds.inWholeMilliseconds
    override val maxHistorySize = 25
    override val jobCleanupInterval = 5.minutes.inWholeMilliseconds
    override val botMessageParseMode = ParseMode.MARKDOWN
}