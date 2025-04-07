package dev.flsrg.bot.db.model

data class User(
    val id: Long,
    val username: String?,
    val messageCount: Int,
    val lastActive: Long,
)