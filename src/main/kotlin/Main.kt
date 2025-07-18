package org.example


import org.example.bot.ResponseHandler
import org.example.bot.TypographyBot

fun main() {
    println("Инициализация компонентов...")
    val responseHandler = ResponseHandler()
    val typographyBot = TypographyBot(responseHandler)
    typographyBot.start()
    println("Бот запущен с новой архитектурой!")
}