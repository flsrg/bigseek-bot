package dev.flsrg.bot.repo

import dev.flsrg.bot.db.User
import dev.flsrg.bot.db.Users
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update

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

    override fun getActiveUsers(days: Int): List<User> = transaction {
        val cutoff = System.currentTimeMillis() - (days * 86_400_000L)
        Users.selectAll()
            .where{ Users.lastActive greaterEq cutoff }
            .map { it.toUser() }
    }

    override fun getOldestUserDate(): Long = transaction {
        Users.selectAll()
            .orderBy(Users.lastActive to SortOrder.ASC)
            .limit(1)
            .singleOrNull()
            ?.get(Users.lastActive)
            ?: System.currentTimeMillis()
    }

    override fun getTotalMessageCount(): Int = transaction {
        Users.select(Users.messagesCount.sum())
            .single()[Users.messagesCount.sum()]
            ?.toInt() ?: 0
    }

    private fun ResultRow.toUser() = User(
        id = this[Users.id],
        username = this[Users.username],
        messageCount = this[Users.messagesCount],
        lastActive = this[Users.lastActive]
    )
}