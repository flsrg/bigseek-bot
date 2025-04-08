package dev.flsrg.bot.db

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Column

data class User(
    val id: Long,
    val username: String?,
    val messageCount: Int,
    val lastActive: Long,
)

object Users : Table("users") {
    val id: Column<Long> = long("id")
    val username: Column<String?> = varchar("username", 32).nullable()
    val messagesCount: Column<Int> = integer("messages_count")
    val lastActive: Column<Long> = long("last_active")

    override val primaryKey = PrimaryKey(id, name = "PK_User_ID")
}

@Serializable
data class HistMessage(
    val role: String,
    val content: String
)

object MessageHistTable : Table("chat_hist") {
    val userId = long("user_id").uniqueIndex()
    val messages = text("messages")
}