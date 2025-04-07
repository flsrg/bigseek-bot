package dev.flsrg.bot.repo

import dev.flsrg.bot.db.Users
import dev.flsrg.bot.db.model.User
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

class SQLUsersRepository: UserRepository {
    override fun recordMessage(userId: Long, username: String?) = transaction {
        // Try to update existing user
        val updated = Users.update({ Users.id eq userId }) {
            it[messagesCount] = SqlExpressionBuilder.run { messagesCount + 1 }
            it[lastActive] = System.currentTimeMillis()
            it[Users.username] = username
        }

        // If no rows updated, insert new user
        if (updated == 0) {
            Users.insert {
                it[id] = userId
                it[Users.username] = username
                it[messagesCount] = 1
                it[lastActive] = System.currentTimeMillis()
            }
        }
    }

    override fun getUsers(): List<User> = transaction {
        Users.selectAll()
            .map { it.toUser() }
    }

    override fun getTotalUserCount(): Int = transaction {
        Users.selectAll().count().toInt()
    }

    override fun getActiveUserCount(days: Int): Int = transaction {
        val cutoff = System.currentTimeMillis() - (days * 86_400_000L)
        Users.select(Users.lastActive)
            .where{ Users.lastActive greaterEq cutoff}
            .count()
            .toInt()
    }

    override fun getOldestUserDate(): Long = transaction {
        Users.selectAll()
            .single()[Users.lastActive.min()] ?: System.currentTimeMillis()
    }

    override fun getTotalMessageCount(): Int = transaction {
        Users.selectAll()
            .single()[Users.messagesCount.sum()] ?: 0
    }

    private fun ResultRow.toUser() = User(
        id = this[Users.id],
        username = this[Users.username],
        messageCount = this[Users.messagesCount],
        lastActive = this[Users.lastActive]
    )
}