package org.example.services

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.File

class LlamaCliServiceImpl(
    private val llamaBinaryPath: String,
    private val modelPath: String,
    private val translationService: TranslationService,
    private val threads: Int = 8,
    private val maxTokens: Int = 500
) : LlmService {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun generateWithHistory(
        systemPrompt: String,
        history: List<Pair<String, String>>,
        newUserPrompt: String
    ): String {
        logger.info("Вызов Llama.cpp с историей, системным промптом и переводом...")

        //val translatedSystemPrompt = translate(systemPrompt, "ru", "en")
        //val translatedNewUserPrompt = translate(newUserPrompt, "ru", "en")
        //val translatedHistory = history.map { (userMsg, assistantMsg) ->
        //    translate(userMsg, "ru", "en") to translate(assistantMsg, "ru", "en")
        //}

        val fullPrompt = buildPrompt(systemPrompt, history, newUserPrompt)
        logger.trace("Собранный английский промпт:\n{}", fullPrompt)

        val command = listOf(
            llamaBinaryPath,
            "-m", modelPath,
            "-p", fullPrompt,
            "-n", maxTokens.toString(),
            "-t", threads.toString(),
            "-c", "4096",
            "--temp", "0.3",
            "--repeat-penalty", "1.1"
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

            val assistantTag = "<|assistant|>"
            val lastAssistantIndex = output.lastIndexOf(assistantTag)
            if (lastAssistantIndex == -1) {
                logger.warn("Не найден тег ассистента. Полный вывод:\n{}", output)
                return output.trim().ifBlank { "Модель не дала структурированный ответ." }
            }

            var responsePart = output.substring(lastAssistantIndex + assistantTag.length)

            val endOfResponseMarker = "> EOF by user"
            val perfMarker = "llama_perf"
            val endOfResponseIndex = responsePart.indexOf(endOfResponseMarker)
            val perfIndex = responsePart.indexOf(perfMarker)

            var englishResponse = responsePart
            if (endOfResponseIndex != -1) {
                englishResponse = responsePart.substring(0, endOfResponseIndex)
            } else if (perfIndex != -1) {
                englishResponse = responsePart.substring(0, perfIndex)
            }
            val cleanEnglishResponse = englishResponse.substringAfterLast("SEP ")

            if (cleanEnglishResponse.isBlank()) {
                logger.warn("Результат после парсинга пуст. Полный вывод:\n{}", output)
                return "Модель не дала ответ на ваш запрос."
            }
            logger.info("Успешно получен английский ответ: '{}'", cleanEnglishResponse)

            //val russianResponse = translate(cleanEnglishResponse, "en", "ru")
            //logger.info("Финальный русский ответ: '{}'", russianResponse)

            return cleanEnglishResponse

        } catch (e: Exception) {
            logger.error("Критическая ошибка при вызове llama-cli", e)
            return "Критическая ошибка при вызове локальной LLM."
        } finally {
            if (process != null && process.isAlive) {
                process.destroyForcibly()
            }
        }
    }

    private fun translate(text: String, source: String, target: String): String {
        if (text.isBlank()) return ""
        logger.debug("Перевод: '{}' ({}-{})", text, source, target)
        return runBlocking {
            translationService.translate(text, source, target)
        } ?: text
    }

    /**
     * Собирает промпт в формате, который понимает модель Phi-3.
     */
    private fun buildPrompt(
        system: String,
        history: List<Pair<String, String>>,
        newUserPrompt: String
    ): String {
        val promptBuilder = StringBuilder()

        promptBuilder.append("<|system|>\n").append(system.trim()).append("<|end|>\n")

        history.forEach { (user, assistant) ->
            promptBuilder.append("<|user|>\n").append(user.trim()).append("<|end|>\n")
            promptBuilder.append("<|assistant|>\n").append(assistant.trim()).append("<|end|>\n")
        }

        promptBuilder.append("<|user|>\n").append(newUserPrompt.trim()).append("<|end|>\n")
        promptBuilder.append("<|assistant|>")

        return promptBuilder.toString()
    }
}