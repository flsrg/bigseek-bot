package dev.flsrg.bot.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

object DatabaseFactory {
    private val log = LoggerFactory.getLogger(javaClass)

    fun init(botName: String) {
        Database.connect(
            url = "jdbc:sqlite:${botName}_bot_data.db",
            driver = "org.sqlite.JDBC",
        )

        transaction {
            SchemaUtils.create(Users)
        }

        log.info("Database connected")
    }
}
