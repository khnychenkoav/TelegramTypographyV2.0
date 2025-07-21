package org.example

import org.example.bot.ResponseHandler
import org.example.bot.TypographyBot
import org.example.calculation.CalculatorService
import org.example.calculation.PriceListProvider
import org.example.processing.JobQueue
import org.example.services.LlamaCliServiceImpl
import org.example.services.TranslationService
import org.example.state.SessionManager
import org.example.utils.TextProvider

fun main() {
    println("Инициализация компонентов...")
    val llamaBinaryPath = "/home/artem/llama.cpp/build/bin/llama-cli"
    val modelPath = "/home/artem/llm_models/Phi-3-mini-4k-instruct-q4.gguf"
    val textProvider = TextProvider("messages_ru.properties")
    val translationService = TranslationService()
    val priceListProvider = PriceListProvider()
    val calculatorService = CalculatorService(priceListProvider, textProvider)
    val localLlmService = LlamaCliServiceImpl(llamaBinaryPath, modelPath, translationService)
    val jobQueue = JobQueue(localLlmService)
    val sessionManager = SessionManager()
    val responseHandler = ResponseHandler(sessionManager, textProvider, jobQueue, calculatorService)
    val typographyBot = TypographyBot(responseHandler)
    typographyBot.start()
    println("Бот запущен!")
}