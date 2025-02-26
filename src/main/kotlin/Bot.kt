package dev.flsrg

import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class Bot(botToken: String?) : TelegramLongPollingBot(botToken) {
    override fun getBotUsername() = "Bigdick"

    override fun onRegister() {
        super.onRegister()
    }

    override fun onUpdateReceived(update: Update) {
        if (update.hasMessage() && update.message.hasText()) {
            val userFirstName = update.message.chat.firstName
            val userLastName = update.message.chat.lastName
            val userId = update.message.chat.id
            val messageText = update.message.text
            val chatId = update.message.chatId

            // For this simple echo bot

            // Log the incoming message and bot response
            log(userFirstName, userLastName, userId.toString(), messageText, messageText)

            val message = SendMessage()
            message.setChatId(chatId)
            message.text = messageText!!

            try {
                execute<Message?, SendMessage?>(message)
            } catch (e: TelegramApiException) {
                e.printStackTrace()
            }
        }
    }

    private fun log(firstName: String?, lastName: String?, userId: String?, text: String?, botAnswer: String?) {
        println("\n----------------------------")
        val dateFormat: DateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
        val date = Date()
        println(dateFormat.format(date))
        println("Message from $firstName $lastName (id = $userId)")
        println("Text: $text")
        println("Bot answer: $botAnswer")
    }
}