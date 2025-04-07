package dev.flsrg.bot.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Column

object Users : Table("users") {
    val id: Column<Long> = long("id")
    val username: Column<String?> = varchar("username", 32).nullable()
    val messagesCount: Column<Int> = integer("messages_count")
    val lastActive: Column<Long> = long("last_active")

    override val primaryKey = PrimaryKey(id, name = "PK_User_ID")
}