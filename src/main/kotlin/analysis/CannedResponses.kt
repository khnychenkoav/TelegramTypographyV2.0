package org.example.analysis

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.*

object CannedResponses {
    private val logger = LoggerFactory.getLogger(javaClass)

    private data class ProcessedResponse(
        val answer: String,
        val threshold: Int,
        val keywordStems: Map<String, Int>
    )

    private val processedResponses: List<ProcessedResponse>

    init {
        logger.info("Инициализация готовых ответов (CannedResponses)...")
        processedResponses = loadAndProcessResponses("canned_responses.json")
        logger.info("Загружено и обработано {} готовых ответов.", processedResponses.size)
    }

    private fun loadAndProcessResponses(resourcePath: String): List<ProcessedResponse> {
        val jsonContent = try {
            this::class.java.classLoader.getResource(resourcePath)?.readText(Charsets.UTF_8)
                ?: throw IllegalStateException("Ресурс не найден: $resourcePath")
        } catch (e: Exception) {
            logger.error("Не удалось прочитать файл готовых ответов: $resourcePath", e)
            return emptyList()
        }

        val json = Json { ignoreUnknownKeys = true }
        val configs = try {
            json.decodeFromString<List<CannedResponseConfig>>(jsonContent)
        } catch (e: Exception) {
            logger.error("Не удалось распарсить JSON из файла: $resourcePath", e)
            return emptyList()
        }

        return configs.map { config ->
            ProcessedResponse(
                answer = config.answer,
                threshold = config.threshold,
                keywordStems = config.keywords.mapKeys { (key, _) ->
                    RussianStemmer.stem(key.lowercase(Locale.getDefault()))
                }
            )
        }
    }

    /**
     * Находит наиболее подходящий готовый ответ на основе системы весов.
     * @param text Входящее сообщение от пользователя.
     * @return Текст готового ответа или null, если подходящего не найдено.
     */
    fun findResponse(text: String): String? {
        val textStems = text.split(Regex("\\s+|[.,!?\"'()]"))
            .mapNotNull { it.trim().lowercase(Locale.getDefault()).ifBlank { null } }
            .map { RussianStemmer.stem(it) }
            .toSet()

        if (textStems.isEmpty()) {
            return null
        }

        var bestMatch: ProcessedResponse? = null
        var maxScore = 0

        for (response in processedResponses) {
            val currentScore = textStems
                .sumOf { stem -> response.keywordStems[stem] ?: 0 }

            if (currentScore >= response.threshold && currentScore > maxScore) {
                maxScore = currentScore
                bestMatch = response
            }
        }

        if (bestMatch != null) {
            logger.debug("Найдено совпадение для готового ответа со счетом {}. Текст: '{}'", maxScore, text)
        }

        return bestMatch?.answer
    }
}