package dev.flsrg.bot

import java.util.Properties

object BotConfig {
    // to avoid "Message is too long" exception
    const val MESSAGE_MAX_LENGTH = 2000
    // to avoid "[429] Too Many Requests:" exception
    const val MESSAGE_SAMPLING_DURATION = 2000L

    fun getBotVersion(): String {
        val props = Properties()
        props.load(this::class.java.classLoader.getResourceAsStream("version.properties"))
        return props.getProperty("bot.version")
    }
}