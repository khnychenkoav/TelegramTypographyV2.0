package org.example.bot

import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.CommandHandlerEnvironment
import com.github.kotlintelegrambot.dispatcher.handlers.TextHandlerEnvironment
import com.github.kotlintelegrambot.entities.ChatId
import org.example.state.SessionManager
import org.example.state.UserMode
import org.example.utils.TextProvider

class ResponseHandler(
    private val sessionManager: SessionManager,
    private val textProvider: TextProvider
) {
    private val keyboardFactory = KeyboardFactory(textProvider)

    fun onStartCommand(env: CommandHandlerEnvironment) {
        val chatId = env.message.chat.id
        sessionManager.resetSession(chatId)
        env.bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            text = textProvider.get("start.welcome")
        )
    }

    fun onTextMessage(env: TextHandlerEnvironment) {
        val chatId = env.message.chat.id
        val text = env.message.text.orEmpty()
        val session = sessionManager.getSession(chatId)

        when (session.mode) {
            UserMode.AWAITING_NAME -> handleNameInput(env, text)
            UserMode.AWAITING_ORDER_DETAILS -> handleOrderDetails(env, text)
            UserMode.AWAITING_OPERATOR_QUERY -> handleOperatorQuery(env, text)
            UserMode.MAIN_MENU -> {
                env.bot.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = textProvider.get("menu.prompt"),
                    replyMarkup = keyboardFactory.buildMainMenu()
                )
            }
        }
    }

    private fun handleNameInput(env: TextHandlerEnvironment, name: String) {
        val chatId = env.message.chat.id
        val newSession = sessionManager.getSession(chatId).copy(
            name = name,
            mode = UserMode.MAIN_MENU
        )
        sessionManager.updateSession(chatId, newSession)

        env.bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            text = textProvider.get("greeting.personal", name),
            replyMarkup = keyboardFactory.buildMainMenu()
        )
    }

    fun onCallbackQuery(env: CallbackQueryHandlerEnvironment) {
        val chatId = env.callbackQuery.message?.chat?.id ?: return
        val callbackData = env.callbackQuery.data
        env.bot.answerCallbackQuery(env.callbackQuery.id)

        when (callbackData) {
            KeyboardFactory.START_CHAT_CALLBACK -> {
                env.bot.sendMessage(ChatId.fromId(chatId), textProvider.get("callback.chat.wip"))
            }
            KeyboardFactory.CALCULATE_ORDER_CALLBACK -> {
                sessionManager.updateSession(chatId, sessionManager.getSession(chatId).copy(mode = UserMode.AWAITING_ORDER_DETAILS))
                env.bot.sendMessage(ChatId.fromId(chatId), textProvider.get("callback.calculate.prompt"))
            }
            KeyboardFactory.CONTACT_OPERATOR_CALLBACK -> {
                sessionManager.updateSession(chatId, sessionManager.getSession(chatId).copy(mode = UserMode.AWAITING_OPERATOR_QUERY))
                env.bot.sendMessage(ChatId.fromId(chatId), textProvider.get("callback.operator.prompt"))
            }
        }
    }

    private fun handleOrderDetails(env: TextHandlerEnvironment, text: String) {
        val chatId = env.message.chat.id
        env.bot.sendMessage(ChatId.fromId(chatId), textProvider.get("order.received", text))
        sessionManager.updateSession(chatId, sessionManager.getSession(chatId).copy(mode = UserMode.MAIN_MENU))
        env.bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            text = textProvider.get("general.what_else"),
            replyMarkup = keyboardFactory.buildMainMenu()
        )
    }

    private fun handleOperatorQuery(env: TextHandlerEnvironment, text: String) {
        val chatId = env.message.chat.id
        env.bot.sendMessage(ChatId.fromId(chatId), textProvider.get("operator.query.received", text))
        sessionManager.updateSession(chatId, sessionManager.getSession(chatId).copy(mode = UserMode.MAIN_MENU))
        env.bot.sendMessage(
            chatId = ChatId.fromId(chatId),
            text = textProvider.get("general.what_else"),
            replyMarkup = keyboardFactory.buildMainMenu()
        )
    }
}