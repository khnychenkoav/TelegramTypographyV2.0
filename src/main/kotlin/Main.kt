package org.example

import org.example.analysis.CannedResponses
import org.example.analysis.ComplexityAnalyzer
import org.example.bot.ResponseHandler
import org.example.bot.TypographyBot
import org.example.calculation.CalculatorService
import org.example.calculation.PriceListProvider
import org.example.processing.JobQueue
import org.example.services.GigaChatServiceImpl
import org.example.services.LlamaCliServiceImpl
import org.example.services.LlmService
import org.example.services.LlmSwitcher
import org.example.services.TranslationService
import org.example.state.SessionManager
import org.example.utils.TextProvider

fun main() {
    println("Инициализация компонентов...")
    val llamaBinaryPath = "/home/artem/llama.cpp/build/bin/llama-cli"
    val modelPath = "/home/artem/llm_models/YandexGPT-5-Lite-8B-instruct-Q4_K_M.gguf"
    val textProvider = TextProvider("messages_ru.properties")
    CannedResponses
    val translationService = TranslationService()
    val priceListProvider = PriceListProvider()
    val calculatorService = CalculatorService(priceListProvider, textProvider)
    val complexityAnalyzer = ComplexityAnalyzer()
    val localLlmService = LlamaCliServiceImpl(llamaBinaryPath, modelPath, translationService)
    val llmService: LlmService = GigaChatServiceImpl()
    LlmSwitcher.initialize(local = localLlmService, giga = llmService)
    val jobQueue = JobQueue()
    val sessionManager = SessionManager()
    val responseHandler = ResponseHandler(sessionManager, textProvider, jobQueue, calculatorService, priceListProvider, complexityAnalyzer)
    val typographyBot = TypographyBot(responseHandler)
    typographyBot.start()
    println("Бот запущен!")
}