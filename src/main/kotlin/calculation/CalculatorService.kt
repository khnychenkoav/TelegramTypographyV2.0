package org.example.calculation

import org.example.calculation.models.CalculationItem
import org.example.calculation.models.CalculationOutcome
import org.example.calculation.models.CalculationResult
import org.example.calculation.models.OrderParameters
import org.example.utils.TextProvider
import org.slf4j.LoggerFactory

class CalculatorService(
    private val prices: PriceListProvider,
    private val textProvider: TextProvider
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Главный метод-маршрутизатор.
     * Анализирует OrderParameters и вызывает соответствующий метод расчета.
     * Теперь он возвращает CalculationResult, преобразуя CalculationOutcome.
     */
    fun calculate(params: OrderParameters): CalculationResult {
        logger.info("Начинаю расчет для продукта типа: {}", params.productType)

        val outcome = when (params.productType.lowercase()) {
            "badge" -> calculateBadges(params)
            "digital_printing" -> calculateDigitalPrinting(params)
            "cutting" -> calculateCutting(params)
            "cutting_and_printing" -> calculateCuttingAndPrinting(params)
            else -> {
                logger.warn("Неизвестный тип продукта для расчета: {}", params.productType)
                CalculationOutcome.Failure(
                    listOf("К сожалению, я пока не умею рассчитывать '${params.productType}'.")
                )
            }
        }

        return when (outcome) {
            is CalculationOutcome.Success -> outcome.result
            is CalculationOutcome.Failure -> CalculationResult(
                items = emptyList(),
                totalWorkPrice = 0.0,
                totalMaterialPrice = 0.0,
                finalTotalPrice = 0.0,
                comments = outcome.errors
            )
        }
    }


    private fun calculateBadges(params: OrderParameters): CalculationOutcome {
        val validationErrors = validateBadgeParams(params)
        if (validationErrors.isNotEmpty()) {
            return CalculationOutcome.Failure(validationErrors)
        }

        val quantity = params.quantity!!
        val badgeTypeKey = "${params.shape!!}_${params.size!!}".lowercase()

        val priceTiers = prices.products.badges.types?.get(badgeTypeKey)
        if (priceTiers == null) {
            val knownTypes = prices.products.badges.types?.keys?.joinToString(", ") ?: "нет доступных"
            return CalculationOutcome.Failure(
                listOf(
                    textProvider.get("calculator.error.unknown_badge_type", badgeTypeKey),
                    textProvider.get("calculator.error.available_badge_types", knownTypes)
                )
            )
        }

        val pricePerItem = priceTiers.find { quantity in it.from..it.to }?.pricePerItem
        if (pricePerItem == null) {
            return CalculationOutcome.Failure(
                listOf(textProvider.get("calculator.error.quantity_out_of_range", quantity))
            )
        }

        val totalPrice = pricePerItem * quantity
        val item = CalculationItem(
            description = "Значки '$badgeTypeKey', $quantity шт. по $pricePerItem ₽",
            workPrice = totalPrice,
            materialPrice = 0.0
        )
        val result = CalculationResult(
            items = listOf(item),
            totalWorkPrice = totalPrice,
            totalMaterialPrice = 0.0,
            finalTotalPrice = totalPrice,
            comments = listOf("Расчет выполнен согласно прайс-листу.")
        )
        return CalculationOutcome.Success(result)
    }

    private fun validateBadgeParams(params: OrderParameters): List<String> {
        val errors = mutableListOf<String>()
        if (params.quantity == null || params.quantity <= 0) {
            errors.add(textProvider.get("calculator.error.no_quantity"))
        }
        if (params.shape.isNullOrBlank() || params.size.isNullOrBlank()) {
            errors.add(textProvider.get("calculator.error.no_shape_or_size"))
        }
        return errors
    }

    private fun calculateDigitalPrinting(params: OrderParameters): CalculationOutcome {
        val validationErrors = validateDigitalPrintingParams(params)
        if (validationErrors.isNotEmpty()) {
            return CalculationOutcome.Failure(validationErrors)
        }

        val quantity = params.quantity!!
        val paperTypeKey = params.material!!
        val printingSides = params.printingSides!!

        val paperPrices = prices.products.digitalPrinting.paperType?.get(paperTypeKey)
        if (paperPrices == null) {
            val knownPaperTypes = prices.products.digitalPrinting.paperType?.keys?.joinToString(", ") ?: "нет доступных"
            return CalculationOutcome.Failure(
                listOf(
                    textProvider.get("calculator.error.unknown_paper_type", paperTypeKey),
                    textProvider.get("calculator.error.available_paper_types", knownPaperTypes)
                )
            )
        }

        val priceTiers = when (printingSides) {
            1 -> paperPrices.oneSided
            2 -> paperPrices.twoSided
            else -> {
                logger.error("Некорректное значение printingSides ($printingSides) прошло валидацию.")
                return CalculationOutcome.Failure(listOf(textProvider.get("calculator.error.invalid_printing_sides")))
            }
        }

        val pricePerItem = priceTiers.find { quantity in it.from..it.to }?.pricePerItem
        if (pricePerItem == null) {
            return CalculationOutcome.Failure(
                listOf(textProvider.get("calculator.error.quantity_out_of_range", quantity))
            )
        }

        val totalPrice = pricePerItem * quantity
        val sidesDescription = if (printingSides == 1) "односторонняя 4+0" else "двухсторонняя 4+4"

        val item = CalculationItem(
            description = "Цифровая печать: '$paperTypeKey' ($sidesDescription), $quantity листов по $pricePerItem ₽",
            workPrice = totalPrice,
            materialPrice = 0.0
        )

        val result = CalculationResult(
            items = listOf(item),
            totalWorkPrice = totalPrice,
            totalMaterialPrice = 0.0,
            finalTotalPrice = totalPrice,
            comments = listOf("Расчет выполнен для печати на листах формата SRA3 (320x450 мм).")
        )

        return CalculationOutcome.Success(result)
    }

    private fun validateDigitalPrintingParams(params: OrderParameters): List<String> {
        val errors = mutableListOf<String>()

        if (params.quantity == null || params.quantity <= 0) {
            errors.add(textProvider.get("calculator.error.no_quantity"))
        }
        if (params.material.isNullOrBlank()) {
            errors.add(textProvider.get("calculator.error.no_paper_type"))
        }
        when (params.printingSides) {
            null -> errors.add(textProvider.get("calculator.error.no_printing_sides"))
            !in 1..2 -> errors.add(textProvider.get("calculator.error.invalid_printing_sides"))
        }

        return errors
    }



    private fun calculateCutting(params: OrderParameters): CalculationOutcome {
        logger.warn("Метод calculateCutting еще не реализован.")
        return notImplementedResult("Резка")
    }

    private fun calculateCuttingAndPrinting(params: OrderParameters): CalculationOutcome {
        logger.warn("Метод calculateCuttingAndPrinting еще не реализован.")
        return notImplementedResult("Резка с печатью")
    }

    private fun notImplementedResult(productName: String): CalculationOutcome.Failure {
        return CalculationOutcome.Failure(listOf(textProvider.get("calculator.error.not_implemented", productName)))
    }
}