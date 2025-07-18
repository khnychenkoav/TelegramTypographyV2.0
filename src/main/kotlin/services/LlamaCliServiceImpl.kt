package org.example.services

import org.slf4j.LoggerFactory
import java.io.File

class LlamaCliServiceImpl(
    private val llamaBinaryPath: String,
    private val modelPath: String,
    private val threads: Int = 4,
    private val maxTokens: Int = 250
) : LlmService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun generate(systemPrompt: String, userPrompt: String): String {
        logger.info("Вызов Llama.cpp (версия 'как в тесте')...")

        val command = listOf(
            llamaBinaryPath,
            "-m", modelPath,
            "-p", userPrompt,
            "-n", maxTokens.toString()
        )

        logger.debug("Команда для запуска: {}", command)

        var process: Process? = null
        try {
            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(File(llamaBinaryPath).parentFile)
            processBuilder.redirectErrorStream(true)
            process = processBuilder.start()

            process.outputStream.close()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            logger.info("Процесс llama-cli завершился с кодом выхода: {}", exitCode)

            if (exitCode != 0) {
                logger.error("llama-cli вернул ошибку. Вывод:\n{}", output)
                return "Ошибка при работе с локальной LLM (код $exitCode)."
            }

            // --- ИСПРАВЛЕННЫЙ ПАРСЕР, ИЩЕТ ПОСЛЕДНЕЕ ВХОЖДЕНИЕ ---
            val assistantTag = "<|assistant|>"
            // Ищем индекс ПОСЛЕДНЕГО вхождения тега
            val lastAssistantIndex = output.lastIndexOf(assistantTag)

            if (lastAssistantIndex == -1) {
                logger.warn("Не найден тег ассистента. Полный вывод:\n{}", output)
                return if (output.trim().isNotBlank()) output.trim() else "Модель не дала структурированный ответ."
            }

            // Берем все, что после ПОСЛЕДНЕГО тега
            var responsePart = output.substring(lastAssistantIndex + assistantTag.length)

            // Остальная логика отсечения "мусора" в конце остается
            val endOfResponseMarker = "> EOF by user"
            val perfMarker = "llama_perf"
            val endOfResponseIndex = responsePart.indexOf(endOfResponseMarker)
            val perfIndex = responsePart.indexOf(perfMarker)

            var finalResponse = responsePart
            if (endOfResponseIndex != -1) {
                finalResponse = responsePart.substring(0, endOfResponseIndex)
            } else if (perfIndex != -1) {
                finalResponse = responsePart.substring(0, perfIndex)
            }

            val cleanResult = finalResponse.trim()
            // --- КОНЕЦ ИСПРАВЛЕННОГО ПАРСЕРА ---

            if (cleanResult.isBlank()) {
                logger.warn("Результат после парсинга пуст. Полный вывод:\n{}", output)
                return "Модель не дала ответ на ваш запрос."
            }

            logger.info("Успешно получен и отпарсен ответ: '{}'", cleanResult)
            return cleanResult

        } catch (e: Exception) {
            logger.error("Критическая ошибка при вызове llama-cli", e)
            return "Критическая ошибка при вызове локальной LLM."
        } finally {
            if (process != null && process.isAlive) {
                logger.warn("Принудительно завершаем процесс llama-cli (финальная проверка).")
                process.destroyForcibly()
            }
        }
    }
}