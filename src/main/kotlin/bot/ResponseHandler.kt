package org.example.bot

import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId

class ResponseHandler {

    fun onStartCommand(env: CommandHandlerEnvironment) {
        val chatId = ChatId.fromId(env.message.chat.id)

        env.bot.sendMessage(
            chatId = chatId,
            text = "Привет из ResponseHandler! Архитектура работает!"
        )
    }
}