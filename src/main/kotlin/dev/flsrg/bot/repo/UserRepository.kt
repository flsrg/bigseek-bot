package dev.flsrg.bot.repo

import dev.flsrg.bot.db.model.User
import java.time.LocalDateTime

interface UserRepository {
    fun recordMessage(userId: Long, username: String?)
    fun getUsers(): List<User>
    fun getTotalUserCount(): Int
    fun getActiveUserCount(days: Int): Int
    fun getOldestUserDate(): Long
    fun getTotalMessageCount(): Int
}