package org.example.bot

import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.TextHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId
import org.example.state.SessionManager
import org.example.state.UserMode

class ResponseHandler(private val sessionManager: SessionManager) {

    fun onStartCommand(env: CommandHandlerEnvironment) {
        val chatId = env.message.chat.id
        sessionManager.resetSession(chatId)

        env.bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            text = "Здравствуйте! Я — виртуальный консультант типографии.\nКак я могу к Вам обращаться?"
        )
    }

    fun onTextMessage(env: TextHandlerEnvironment) {
        val chatId = env.message.chat.id
        val text = env.message.text.orEmpty()
        val session = sessionManager.getSession(chatId)

        when (session.mode) {
            UserMode.AWAITING_NAME -> handleNameInput(env, text)
            UserMode.CONVERSATION -> handleConversation(env, text)
        }
    }

    private fun handleNameInput(env: TextHandlerEnvironment, name: String) {
        val chatId = env.message.chat.id
        val newSession = sessionManager.getSession(chatId).copy(
            name = name,
            mode = UserMode.CONVERSATION
        )
        sessionManager.updateSession(chatId, newSession)

        env.bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            text = "Приятно познакомиться, $name! Чем могу помочь?"
        )
    }

    private fun handleConversation(env: TextHandlerEnvironment, text: String) {
        val chatId = env.message.chat.id
        val session = sessionManager.getSession(chatId)
        val userName = session.name ?: "пользователь"

        env.bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            text = "$userName, вы написали: \"$text\""
        )
    }
}