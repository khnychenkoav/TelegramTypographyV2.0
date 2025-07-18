package org.example.services


interface LlmService {
    /**
     * Генерирует ответ на основе промпта.
     * @param systemPrompt Системная инструкция для модели.
     * @param userPrompt Запрос пользователя.
     * @return Сгенерированный ответ.
     */
    fun generate(systemPrompt: String, userPrompt: String): String
}