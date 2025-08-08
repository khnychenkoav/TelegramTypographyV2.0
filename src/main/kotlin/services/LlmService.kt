package org.example.services


interface LlmService {
    /**
     * Генерирует ответ на новый запрос пользователя, учитывая предыдущую историю диалога.
     * @param systemPrompt Системная инструкция для модели.
     * @param history Список пар "вопрос пользователя" -> "ответ модели".
     * @param newUserPrompt Новый, текущий запрос пользователя.
     * @return Сгенерированный ответ.
     */
    suspend fun generateWithHistory(
        systemPrompt: String,
        history: List<Pair<String, String>>,
        newUserPrompt: String
    ): String
}