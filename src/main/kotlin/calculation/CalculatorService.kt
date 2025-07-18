package org.example.calculation

import org.example.calculation.models.CalculationResult
import org.example.calculation.models.CalculationItem
import org.example.calculation.models.OrderParameters
import org.slf4j.LoggerFactory

class CalculatorService(private val prices: PriceListProvider) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Главный метод-маршрутизатор.
     * Анализирует OrderParameters и вызывает соответствующий метод расчета.
     */
    fun calculate(params: OrderParameters): CalculationResult {
        logger.info("Начинаю расчет для продукта типа: {}", params.productType)

        return when (params.productType) {
            "badge" -> calculateBadges(params)
            "digital_printing" -> calculateDigitalPrinting(params)
            "cutting" -> calculateCutting(params)
            "cutting_and_printing" -> calculateCuttingAndPrinting(params)
            else -> {
                logger.warn("Неизвестный тип продукта для расчета: {}", params.productType)
                CalculationResult(
                    items = emptyList(),
                    totalWorkPrice = 0.0,
                    totalMaterialPrice = 0.0,
                    finalTotalPrice = 0.0,
                    comments = listOf("К сожалению, я пока не умею рассчитывать '${params.productType}'.")
                )
            }
        }
    }

    private fun calculateBadges(params: OrderParameters): CalculationResult {
        val quantity = params.quantity
        if (quantity == null || quantity <= 0) {
            return errorResult("Не указано количество значков.")
        }

        val badgeTypeKey = "${params.shape}_${params.size}".toLowerCase()
        val priceTiers = prices.products.badges.types?.get(badgeTypeKey)
        if (priceTiers == null) {
            return errorResult("Не могу найти цену для значков типа '${badgeTypeKey}'. Уточните форму и размер.")
        }

        val pricePerItem = priceTiers.find { quantity >= it.from && quantity <= it.to }?.pricePerItem
        if (pricePerItem == null) {
            return errorResult("Для количества ${quantity} шт. не найдена цена. Возможно, это слишком большой тираж для автоматического расчета.")
        }

        val totalPrice = pricePerItem * quantity

        val item = CalculationItem(
            description = "Значки '${badgeTypeKey}', ${quantity} шт. по ${pricePerItem} ₽",
            workPrice = totalPrice,
            materialPrice = 0.0
        )

        return CalculationResult(
            items = listOf(item),
            totalWorkPrice = totalPrice,
            totalMaterialPrice = 0.0,
            finalTotalPrice = totalPrice,
            comments = listOf("Расчет выполнен согласно прайс-листу.")
        )
    }


    private fun calculateDigitalPrinting(params: OrderParameters): CalculationResult {
        // TODO: Реализовать логику расчета для листовок, буклетов и т.д.
        logger.warn("Метод calculateDigitalPrinting еще не реализован.")
        return notImplementedResult("Цифровая печать")
    }

    private fun calculateCutting(params: OrderParameters): CalculationResult {
        // TODO: Реализовать логику расчета для фрезерной, лазерной резки
        logger.warn("Метод calculateCutting еще не реализован.")
        return notImplementedResult("Резка")
    }

    private fun calculateCuttingAndPrinting(params: OrderParameters): CalculationResult {
        // TODO: Реализовать сложную логику, объединяющую резку, печать, материалы и проверку на мин. сумму
        logger.warn("Метод calculateCuttingAndPrinting еще не реализован.")
        return notImplementedResult("Резка с печатью")
    }


    private fun errorResult(errorMessage: String): CalculationResult {
        return CalculationResult(
            items = emptyList(),
            totalWorkPrice = 0.0,
            totalMaterialPrice = 0.0,
            finalTotalPrice = 0.0,
            comments = listOf(errorMessage)
        )
    }

    private fun notImplementedResult(productName: String): CalculationResult {
        return errorResult("Расчет для продукта '$productName' еще не реализован.")
    }
}