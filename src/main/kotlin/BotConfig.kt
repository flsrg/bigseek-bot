package dev.flsrg

import java.util.Properties

object BotConfig {
    fun getBotVersion(): String {
        val props = Properties()
        props.load(this::class.java.classLoader.getResourceAsStream("version.properties"))
        return props.getProperty("bot.version")
    }
}