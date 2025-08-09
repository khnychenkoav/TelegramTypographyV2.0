package org.example.analysis

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.util.*

class ComplexityAnalyzer {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val keywordStems: Map<String, Int>

    init {
        logger.info("Инициализация анализатора сложности...")
        keywordStems = loadAndProcessRules("complexity_rules.json")
        logger.info("Загружено {} правил для определения сложности.", keywordStems.size)
    }

    private fun loadAndProcessRules(resourcePath: String): Map<String, Int> {
        val jsonContent = try {
            this::class.java.classLoader.getResource(resourcePath)?.readText(Charsets.UTF_8)
                ?: throw IllegalStateException("Ресурс не найден: $resourcePath")
        } catch (e: Exception) {
            logger.error("Не удалось прочитать файл правил сложности: $resourcePath", e)
            return emptyMap()
        }

        val json = Json { ignoreUnknownKeys = true }
        val config = try {
            json.decodeFromString<ComplexityRulesConfig>(jsonContent)
        } catch (e: Exception) {
            logger.error("Не удалось распарсить JSON из файла: $resourcePath", e)
            return emptyMap()
        }

        val allKeywords = config.complexity_keywords + config.simplifying_keywords

        return allKeywords.mapKeys { (key, _) ->
            RussianStemmer.stem(key.lowercase(Locale.getDefault()))
        }
    }

    /**
     * Анализирует текст и возвращает числовой балл сложности.
     * @param text Входящее сообщение от пользователя.
     * @return Целое число. Чем оно выше, тем сложнее запрос. Может быть отрицательным.
     */
    fun analyze(text: String): Int {
        val textStems = text.split(Regex("\\s+|[.,!?\"'()]"))
            .mapNotNull { it.trim().lowercase(Locale.getDefault()).ifBlank { null } }
            .map { RussianStemmer.stem(it) }
            .toSet()

        if (textStems.isEmpty()) {
            return 0
        }

        val score = textStems.sumOf { stem -> keywordStems[stem] ?: 0 }

        logger.debug("Анализ сложности для '{}': {}. Счет: {}", text, textStems, score)
        return score
    }
}