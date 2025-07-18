package org.example

import org.example.bot.ResponseHandler
import org.example.bot.TypographyBot
import org.example.state.SessionManager

fun main() {
    println("Инициализация компонентов...")
    val sessionManager = SessionManager()
    val responseHandler = ResponseHandler(sessionManager)
    val typographyBot = TypographyBot(responseHandler)
    typographyBot.start()
    println("Бот запущен с новой архитектурой!")
}