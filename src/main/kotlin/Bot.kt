package dev.flsrg

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.exceptions.TelegramApiException

class Bot(botToken: String?) : TelegramLongPollingBot(botToken) {
    val logger: Logger = LoggerFactory.getLogger(Bot::class.java)

    override fun getBotUsername() = "Bigdick"

    override fun onRegister() {
        super.onRegister()
    }

    override fun onUpdateReceived(update: Update) {
        if (update.hasMessage() && update.message.hasText()) {
            val userFirstName = update.message.chat.firstName
            val chatId = update.message.chatId.toString()
            val messageText = update.message.text
            logger.info("Received message from {}: {}", userFirstName, messageText)

            val message = SendMessage()
            message.chatId = chatId
            message.text = messageText!!

            try {
                execute<Message?, SendMessage?>(message)
                logger.info("Sent message: {}", messageText)
            } catch (e: TelegramApiException) {
                logger.error("Error while sending message", e)
            }
        }
    }
}