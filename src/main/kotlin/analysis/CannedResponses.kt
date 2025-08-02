package org.example.analysis

import java.io.InputStream
import java.util.*

object CannedResponses {
    private val responses: Map<String, Pair<List<String>, String>>

    init {
        val responseNames = listOf("address", "price")

        val loadedResponses = mutableMapOf<String, Pair<List<String>, String>>()
        for (name in responseNames) {
            try {
                val keywords = loadResourceLines("canned/${name}_keywords.txt")
                val answerText = loadResourceText("canned/${name}.txt")
                if (keywords.isNotEmpty() && answerText.isNotBlank()) {
                    loadedResponses[name] = Pair(keywords, answerText)
                }
            } catch (e: Exception) {
                println("ПРЕДУПРЕЖДЕНИЕ: Не удалось загрузить готовый ответ '$name'. Ошибка: ${e.message}")
            }
        }
        responses = loadedResponses
        println("Загружено готовых ответов: ${responses.size}")
    }

    private fun loadResourceLines(path: String): List<String> {
        return javaClass.classLoader.getResourceAsStream(path)?.bufferedReader()?.readLines() ?: emptyList()
    }

    private fun loadResourceText(path: String): String {
        return javaClass.classLoader.getResourceAsStream(path)?.bufferedReader()?.readText() ?: ""
    }

    /**
     * Ищет в тексте пользователя ключевые слова и возвращает готовый ответ.
     */
    fun findResponse(text: String): String? {
        val lowerCaseText = text.lowercase(Locale.getDefault())

        for ((_, data) in responses) {
            val (keywords, answer) = data
            if (keywords.any { lowerCaseText.contains(it) }) {
                return answer
            }
        }
        return null
    }
}