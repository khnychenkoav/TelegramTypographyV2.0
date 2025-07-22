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
        val validationErrors = validateCuttingParams(params)
        if (validationErrors.isNotEmpty()) {
            return CalculationOutcome.Failure(validationErrors)
        }

        val material = params.material!!
        val (areaSqm, perimeterM) = calculateDimensions(params)

        val materialPricePerSqm = findMaterialPricePerSqm(params.originalMaterialKey)
            ?: return CalculationOutcome.Failure(listOf(textProvider.get("calculator.error.unknown_material_key", params.originalMaterialKey.orEmpty())))

        val totalMaterialPrice = areaSqm * materialPricePerSqm
        val materialItem = CalculationItem("Материал: ${params.material}", 0.0, totalMaterialPrice)

        val cuttingWorkPrice: Double
        val workItem: CalculationItem

        if (params.thicknessMm != null && params.thicknessMm > 0) {
            val cuttingPricePerMeter = findCuttingPricePerMeter(material, params.thicknessMm)
                ?: return CalculationOutcome.Failure(listOf(textProvider.get("calculator.error.unknown_operation_key", "$material ${params.thicknessMm}мм")))

            cuttingWorkPrice = perimeterM * cuttingPricePerMeter
            workItem = CalculationItem("Работа: Фрезерная резка", cuttingWorkPrice, 0.0)
        } else {
            val plotterPricePerMeter = prices.operations.cutting.plotternaya?.pricePerMeter
                ?: return CalculationOutcome.Failure(listOf("Стоимость плоттерной резки не найдена в прайс-листе."))

            cuttingWorkPrice = perimeterM * plotterPricePerMeter
            workItem = CalculationItem("Работа: Плоттерная резка", cuttingWorkPrice, 0.0)
        }

        var totalWorkPrice = cuttingWorkPrice
        val comments = mutableListOf<String>()

        var finalPrice = totalMaterialPrice + cuttingWorkPrice

        val minOrderPrice = prices.operations.generalRules.minOrderOneMachine
        if (finalPrice < minOrderPrice) {
            comments.add(textProvider.get("calculator.comment.min_order_applied", minOrderPrice.toInt()))
            finalPrice = minOrderPrice
        }

        val result = CalculationResult(
            items = listOf(materialItem, workItem),
            totalWorkPrice = cuttingWorkPrice,
            totalMaterialPrice = totalMaterialPrice,
            finalTotalPrice = finalPrice,
            comments = comments.ifEmpty { listOf("Расчет выполнен согласно прайс-листу.") }
        )

        return CalculationOutcome.Success(result)
    }

    private fun validateCuttingParams(params: OrderParameters): List<String> {
        val errors = mutableListOf<String>()

        if (params.material.isNullOrBlank()) {
            errors.add(textProvider.get("calculator.error.no_paper_type"))
        }

        val hasRectDimensions = params.widthCm != null && params.heightCm != null && params.widthCm > 0 && params.heightCm > 0
        val hasCircleDimensions = params.diameterCm != null && params.diameterCm > 0

        if (!hasRectDimensions && !hasCircleDimensions) {
            errors.add(textProvider.get("calculator.error.no_dimensions"))
        }

        return errors
    }

    /**
     * Рассчитывает площадь (в м²) и периметр (в м) на основе параметров заказа.
     * @return Pair<Площадь, Периметр>
     */
    private fun calculateDimensions(params: OrderParameters): Pair<Double, Double> {
        return if (params.diameterCm != null && params.diameterCm > 0) {
            val diameterM = params.diameterCm / 100.0
            val radiusM = diameterM / 2.0
            val area = kotlin.math.PI * radiusM * radiusM
            val perimeter = kotlin.math.PI * diameterM
            area to perimeter
        } else {
            val widthM = params.widthCm!! / 100.0
            val heightM = params.heightCm!! / 100.0
            val area = widthM * heightM
            val perimeter = 2 * (widthM + heightM)
            area to perimeter
        }
    }

    /**
     * Ищет цену за квадратный метр материала по его ключу (например, "фанера_3мм").
     */
    private fun findMaterialPricePerSqm(originalKey: String?): Double? {
        if (originalKey.isNullOrBlank()) return null

        val allMaterials = prices.materials.wood + prices.materials.plastic + prices.materials.composite +
                prices.materials.film + prices.materials.magnetic + prices.materials.foam + prices.materials.adhesive

        return allMaterials[originalKey.lowercase()]
    }

    /**
     * Ищет цену за погонный метр резки, подбирая подходящий диапазон.
     * Например, для "фанера" и толщины 4мм найдет ключ "фанера_3-6мм".
     */
    private fun findCuttingPricePerMeter(material: String, thickness: Double): Double? {
        val cuttingPrices = prices.operations.cutting.frezernaya.prices

        val materialAliases = mapOf(
            "композитные_панели" to "композит",
            "фанера" to "фанера",
            "мдф" to "мдф",
            "акрил" to "акрил",
            "пвх" to "пвх"
        )

        val matchingKey = cuttingPrices.keys.find { key ->
            val baseMaterialFromKey = key.substringBeforeLast('_')

            val alias = materialAliases[baseMaterialFromKey]

            if (alias == null || !material.contains(alias, ignoreCase = true)) {
                return@find false
            }

            val thicknessRangeString = key.substringAfterLast('_').removeSuffix("мм")
            val parts = thicknessRangeString.split('-')
            if (parts.size == 2) {
                val from = parts[0].toDoubleOrNull()
                val to = parts[1].toDoubleOrNull()
                if (from != null && to != null) {
                    return@find thickness >= from && thickness <= to
                }
            } else {
                val singleThickness = thicknessRangeString.toDoubleOrNull()
                if (singleThickness != null) {
                    return@find thickness == singleThickness
                }
            }
            false
        }

        return matchingKey?.let { cuttingPrices[it] }
    }

    private fun calculateCuttingAndPrinting(params: OrderParameters): CalculationOutcome {
        val validationErrors = validateCuttingAndPrintingParams(params)
        if (validationErrors.isNotEmpty()) {
            return CalculationOutcome.Failure(validationErrors)
        }

        val material = params.material!!
        val printingLayers = params.printingLayers!!
        val (areaSqm, perimeterM) = calculateDimensions(params)

        val materialPricePerSqm = findMaterialPricePerSqm(params.originalMaterialKey)
            ?: return CalculationOutcome.Failure(listOf(textProvider.get("calculator.error.unknown_material_key", params.originalMaterialKey.orEmpty())))

        val totalMaterialPrice = areaSqm * materialPricePerSqm
        val materialItem = CalculationItem("Материал: $material", 0.0, totalMaterialPrice)

        val cuttingWorkPrice: Double
        val cuttingItem: CalculationItem

        if (params.thicknessMm != null && params.thicknessMm > 0) {
            val cuttingPricePerMeter = findCuttingPricePerMeter(material, params.thicknessMm)
                ?: return CalculationOutcome.Failure(listOf(textProvider.get("calculator.error.unknown_operation_key", "$material ${params.thicknessMm}мм")))

            cuttingWorkPrice = perimeterM * cuttingPricePerMeter
            cuttingItem = CalculationItem("Работа: Фрезерная резка", cuttingWorkPrice, 0.0)
        } else {
            val plotterPricePerMeter = prices.operations.cutting.plotternaya?.pricePerMeter
                ?: return CalculationOutcome.Failure(listOf("Стоимость плоттерной резки не найдена в прайс-листе."))

            cuttingWorkPrice = perimeterM * plotterPricePerMeter
            cuttingItem = CalculationItem("Работа: Плоттерная резка", cuttingWorkPrice, 0.0)
        }

        var printingWorkPrice = findUvPrintingPricePerSqm(printingLayers)?.let { pricePerSqm ->
            areaSqm * pricePerSqm
        } ?: return CalculationOutcome.Failure(listOf(textProvider.get("calculator.error.unknown_print_operation_key", "УФ-печать $printingLayers слоя")))

        if (material.contains("фанера", ignoreCase = true) || material.contains("мдф", ignoreCase = true)) {
            printingWorkPrice *= prices.operations.printing.uvFlatbed.surcharges.woodSurfaceMultiplier
        }
        val printingItem = CalculationItem("Работа: УФ-печать ($printingLayers слоя)", printingWorkPrice, 0.0)

        var totalWorkPrice = cuttingWorkPrice + printingWorkPrice
        val comments = mutableListOf<String>()
        var finalPrice = totalMaterialPrice + cuttingWorkPrice + printingWorkPrice

        val minOrderPrice = prices.operations.generalRules.minOrderMultiMachine
        if (finalPrice < minOrderPrice) {
            comments.add(textProvider.get("calculator.comment.min_order_multi_applied", minOrderPrice.toInt()))
            finalPrice = minOrderPrice
        }

        val result = CalculationResult(
            items = listOf(materialItem, cuttingItem, printingItem),
            totalWorkPrice = cuttingWorkPrice + printingWorkPrice,
            totalMaterialPrice = totalMaterialPrice,
            finalTotalPrice = finalPrice,
            comments = comments.ifEmpty { listOf("Расчет выполнен согласно прайс-листу.") }
        )

        return CalculationOutcome.Success(result)
    }

    private fun validateCuttingAndPrintingParams(params: OrderParameters): List<String> {
        val errors = validateCuttingParams(params).toMutableList()

        when (params.printingLayers) {
            null -> errors.add(textProvider.get("calculator.error.no_printing_layers"))
            !in 1..3 -> errors.add(textProvider.get("calculator.error.invalid_printing_layers"))
        }

        return errors
    }

    /**
     * Ищет цену за квадратный метр УФ-печати по количеству слоев.
     */
    private fun findUvPrintingPricePerSqm(layers: Int): Double? {
        val printingPrices = prices.operations.printing.uvFlatbed.prices
        val key = when (layers) {
            1 -> "color_or_white_or_varnish_1_layer"
            2 -> "color_with_white_2_layers"
            3 -> "color_with_white_and_varnish_3_layers"
            else -> null
        }
        return key?.let { printingPrices[it] }
    }


    private fun notImplementedResult(productName: String): CalculationOutcome.Failure {
        return CalculationOutcome.Failure(listOf(textProvider.get("calculator.error.not_implemented", productName)))
    }
}