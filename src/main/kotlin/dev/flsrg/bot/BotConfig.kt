package dev.flsrg.bot

import java.util.Properties

object BotConfig {
    const val MESSAGE_MAX_LENGTH = 3000

    fun getBotVersion(): String {
        val props = Properties()
        props.load(this::class.java.classLoader.getResourceAsStream("version.properties"))
        return props.getProperty("bot.version")
    }
}