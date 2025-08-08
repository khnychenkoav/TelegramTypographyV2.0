package org.example.state

import org.example.calculation.models.OrderParameters

enum class UserMode {
    AWAITING_NAME,          // Ожидание ввода имени при первом запуске
    MAIN_MENU,              // Главное меню с кнопками
    LLM_CHAT,               // Свободный диалог с локальной LLM
    AWAITING_OPERATOR_QUERY,// Ожидание текста для пересылки оператору

    CALC_AWAITING_PRODUCT_TYPE, // Шаг 1: Пользователь должен выбрать тип продукта (значки, резка и т.д.)
    CALC_AWAITING_QUANTITY,     // Общий шаг: Ожидание ввода количества (для любого продукта)

    CALC_AWAITING_BADGE_TYPE,   // Ожидание выбора конкретного типа значка (круглый_37мм)

    CALC_AWAITING_PAPER_TYPE,   // Ожидание выбора типа бумаги
    CALC_AWAITING_PRINT_SIDES,  // Ожидание выбора сторон печати (1 или 2)

    CALC_AWAITING_MATERIAL_CATEGORY,      // Ожидание выбора КАТЕГОРИИ материала
    CALC_AWAITING_MATERIAL_AND_THICKNESS, // Ожидание выбора КОНКРЕТНОГО материала
    CALC_AWAITING_DIMENSIONS,             // Ожидание ввода размеров (ширина/высота или диаметр)
    CALC_AWAITING_PRINT_LAYERS,           // Ожидание ввода слоев печати (только для резки+печати)

    CALC_AWAITING_AI_ESTIMATION,

    AWAITING_FILE_CAPTION,

    AWAITING_NEWS_MESSAGE,
}

data class CalculationData(
    var originalMaterialKey: String? = null,
    var productType: String? = null,
    var shape: String? = null,
    var size: String? = null,
    var quantity: Int? = null,
    var material: String? = null,
    var thicknessMm: Double? = null,
    var widthCm: Double? = null,
    var heightCm: Double? = null,
    var diameterCm: Double? = null,
    var printingLayers: Int? = null,
    var printingSides: Int? = null
) {
    /**
     * Преобразует накопленные данные в OrderParameters для передачи в CalculatorService.
     * Вызывается, когда все необходимые данные собраны.
     */
    fun toOrderParameters(): OrderParameters? {
        val finalProductType = productType ?: return null
        return OrderParameters(
            originalMaterialKey = originalMaterialKey,
            productType = finalProductType,
            shape = shape,
            size = size,
            quantity = quantity,
            material = material,
            thicknessMm = thicknessMm,
            widthCm = widthCm,
            heightCm = heightCm,
            diameterCm = diameterCm,
            printingLayers = printingLayers,
            printingSides = printingSides
        )
    }
}


data class UserSession(
    val userId: Long = 0,
    val mode: UserMode = UserMode.AWAITING_NAME,
    val name: String? = null,
    val conversationHistory: MutableList<Pair<String, String>> = mutableListOf(),
    var currentCalculation: CalculationData? = null,
    var lastBotMessageId: Long? = null
)