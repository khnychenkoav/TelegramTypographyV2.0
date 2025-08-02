package org.example.services

enum class LlmType {
    LOCAL, GIGA_CHAT
}

/**
 * Синглтон-объект для управления и переключения между различными реализациями LlmService.
 */
object LlmSwitcher {
    private lateinit var localLlm: LlmService
    private lateinit var gigaChat: LlmService

    private var currentMode: LlmType = LlmType.GIGA_CHAT

    /**
     * Инициализирует свитчер, передавая ему созданные экземпляры LLM-сервисов.
     * Этот метод должен быть вызван один раз при старте приложения.
     */
    fun initialize(local: LlmService, giga: LlmService) {
        localLlm = local
        gigaChat = giga
    }

    /**
     * Возвращает текущий активный экземпляр LlmService.
     */
    fun getCurrentLlmService(): LlmService {
        return when (currentMode) {
            LlmType.LOCAL -> localLlm
            LlmType.GIGA_CHAT -> gigaChat
        }
    }

    /**
     * Переключает режим работы и возвращает текстовое описание нового режима.
     */
    fun switchTo(mode: LlmType): String {
        currentMode = mode
        return when (mode) {
            LlmType.LOCAL -> "Режим переключен на локальную LLM (Phi-3 Mini)."
            LlmType.GIGA_CHAT -> "Режим переключен на GigaChat."
        }
    }
}