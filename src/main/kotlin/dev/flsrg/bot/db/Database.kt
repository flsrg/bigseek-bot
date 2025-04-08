package dev.flsrg.bot.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

object Database {
    private val log = LoggerFactory.getLogger(javaClass)
    lateinit var database: Database

    fun init(botName: String) {
        database = Database.connect(
            url = "jdbc:sqlite:${botName}_bot_data.db",
            driver = "org.sqlite.JDBC",
        )

        transaction(database) {
            SchemaUtils.create(Users)
            SchemaUtils.create(MessageHistTable)
        }

        log.info("Database connected")
    }
}
