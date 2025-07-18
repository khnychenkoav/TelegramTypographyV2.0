package org.example.bot

import com.github.kotlintelegrambot.dispatcher.handlers.CallbackQueryHandlerEnvironment
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
            UserMode.AWAITING_ORDER_DETAILS -> handleOrderDetails(env, text)
            UserMode.AWAITING_OPERATOR_QUERY -> handleOperatorQuery(env, text)
            UserMode.MAIN_MENU -> {
                env.bot.sendMessage(
                    chatId = ChatId.fromId(chatId),
                    text = "Пожалуйста, выберите один из вариантов в меню ниже.",
                    replyMarkup = KeyboardFactory.buildMainMenu()
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
            text = "Приятно познакомиться, $name! Чем могу помочь?",
            replyMarkup = KeyboardFactory.buildMainMenu()
        )
    }


    fun onCallbackQuery(env: CallbackQueryHandlerEnvironment) {
        val chatId = env.callbackQuery.message?.chat?.id ?: return
        val callbackData = env.callbackQuery.data
        env.bot.answerCallbackQuery(env.callbackQuery.id)
        when (callbackData) {
            KeyboardFactory.START_CHAT_CALLBACK -> {
                env.bot.sendMessage(ChatId.fromId(chatId), "Вы выбрали 'Начать диалог'. Этот функционал будет добавлен позже.")
            }
            KeyboardFactory.CALCULATE_ORDER_CALLBACK -> {
                sessionManager.updateSession(chatId, sessionManager.getSession(chatId).copy(mode = UserMode.AWAITING_ORDER_DETAILS))
                env.bot.sendMessage(ChatId.fromId(chatId), "Пожалуйста, опишите ваш заказ для расчета стоимости.")
            }
            KeyboardFactory.CONTACT_OPERATOR_CALLBACK -> {
                sessionManager.updateSession(chatId, sessionManager.getSession(chatId).copy(mode = UserMode.AWAITING_OPERATOR_QUERY))
                env.bot.sendMessage(ChatId.fromId(chatId), "Напишите ваш вопрос, и я передам его оператору.")
            }
        }
    }

    private fun handleOrderDetails(env: TextHandlerEnvironment, text: String) {
        val chatId = env.message.chat.id
        env.bot.sendMessage(ChatId.fromId(chatId), "Спасибо! Ваш заказ \"$text\" принят в обработку. Скоро здесь будет результат расчета.")
        sessionManager.updateSession(chatId, sessionManager.getSession(chatId).copy(mode = UserMode.MAIN_MENU))
        env.bot.sendMessage(ChatId.fromId(chatId), "Чем еще могу помочь?", replyMarkup = KeyboardFactory.buildMainMenu())
    }

    private fun handleOperatorQuery(env: TextHandlerEnvironment, text: String) {
        val chatId = env.message.chat.id
        env.bot.sendMessage(ChatId.fromId(chatId), "Ваш вопрос \"$text\" передан оператору. Он скоро с вами свяжется.")
        sessionManager.updateSession(chatId, sessionManager.getSession(chatId).copy(mode = UserMode.MAIN_MENU))
        env.bot.sendMessage(ChatId.fromId(chatId), "Чем еще могу помочь?", replyMarkup = KeyboardFactory.buildMainMenu())
    }
}