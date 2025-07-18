package org.example

import org.example.bot.ResponseHandler
import org.example.bot.TypographyBot
import org.example.state.SessionManager
import org.example.utils.TextProvider

fun main() {
    println("Инициализация компонентов...")
    val textProvider = TextProvider("messages_ru.properties")
    val sessionManager = SessionManager()
    val responseHandler = ResponseHandler(sessionManager, textProvider)
    val typographyBot = TypographyBot(responseHandler)
    typographyBot.start()
    println("Бот запущен с новой архитектурой!")
}