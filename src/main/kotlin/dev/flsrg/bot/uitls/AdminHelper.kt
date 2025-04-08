package dev.flsrg.bot.uitls

import dev.flsrg.bot.Bot
import dev.flsrg.bot.repo.UserRepository
import dev.flsrg.bot.uitls.BotUtils.botMessage
import dev.flsrg.llmpollingclient.client.OpenRouterClient
import org.telegram.telegrambots.meta.api.objects.Update

class AdminHelper(
    private val bot: Bot,
    private val adminUserId: Long,
    private val userRepository: UserRepository,
) {
    companion object {
        private const val ADMIN_STATS_COMMAND = "/stats"
    }

    fun isAdminCommand(update: Update) = update.message.text == ADMIN_STATS_COMMAND

    fun handleAdminCommand(update: Update) {
        if (update.message.from.id != adminUserId) return

        if (update.message.text == ADMIN_STATS_COMMAND) {
            sendStatistics(update.message.chatId.toString())
        }
    }

    fun updateUserMessage(userId: Long, userName: String, message: OpenRouterClient.ChatMessage) {
        userRepository.recordMessage(userId, userName)
    }

    fun sendStatistics(adminChatId: String) = bot.apply {
        try {
            val stats = """
                📊 *Bot Statistics Report*
                
                👥 *Total Users:* ${userRepository.getTotalUserCount()}
                🟢 *Active Users (30 days):* ${userRepository.getActiveUserCount(30)}
                💬 *Total Messages Processed:* ${userRepository.getTotalMessageCount()}
                📈 *Daily Messages (Avg):* ${getDailyMessageAverage()}
                👤 *Most Active User:* ${getMostActiveUser()}
            """.trimIndent()

            execute(
                botMessage(
                    chatId = adminChatId,
                    message = stats,
                )
            )
        } catch (e: Exception) {
            execute(
                botMessage(
                    chatId = adminChatId,
                    message = "❌ Error generating statistics: ${e.message}"
                )
            )
            throw e
        }
    }

    private fun getDailyMessageAverage(): String {
        val totalMessages = userRepository.getTotalMessageCount()
        val daysActive = (System.currentTimeMillis() - userRepository.getOldestUserDate()) / (24 * 60 * 60 * 1000)

        return "%.1f".format(totalMessages.toDouble() / daysActive)
    }

    private fun getMostActiveUser(): String {
        val mostActive = userRepository.getUsers().maxBy {
            it.messageCount
        }
        return "${mostActive.username ?: "Anonymous"} (${mostActive.messageCount} messages)"
    }
}