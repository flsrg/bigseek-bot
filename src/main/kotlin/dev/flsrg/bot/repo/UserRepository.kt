package dev.flsrg.bot.repo

import dev.flsrg.bot.db.User

interface UserRepository {
    fun recordMessage(userId: Long, username: String?)
    fun getUsers(): List<User>
    fun getTotalUserCount(): Int
    fun getActiveUsers(days: Int): List<User>
    fun getOldestUserDate(): Long
    fun getTotalMessageCount(): Int
}