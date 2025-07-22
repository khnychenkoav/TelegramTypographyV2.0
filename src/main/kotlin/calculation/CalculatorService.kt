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

    fun calculate(params: OrderParameters): CalculationResult {
        logger.info("Начинаю расчет для продукта типа: {}", params.productType)
        val outcome = when (params.productType.lowercase()) {
            "badge" -> calculateBadges(params)
            "digital_printing" -> calculateDigitalPrinting(params)
            "cutting" -> calculateCutting(params)
            "cutting_and_printing" -> calculateCuttingAndPrinting(params)
            else -> CalculationOutcome.Failure(listOf("К сожалению, я пока не умею рассчитывать '${params.productType}'."))
        }

        return when (outcome) {
            is CalculationOutcome.Success -> outcome.result
            is CalculationOutcome.Failure -> CalculationResult(emptyList(), 0.0, 0.0, 0.0, outcome.errors)
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
            ?: return CalculationOutcome.Failure(
                listOf(
                    textProvider.get("calculator.error.unknown_badge_type", badgeTypeKey),
                    textProvider.get("calculator.error.available_badge_types", prices.products.badges.types?.keys?.joinToString(", ") ?: "нет доступных")
                )
            )

        val pricePerItem = priceTiers.find { quantity in it.from..it.to }?.pricePerItem
            ?: return CalculationOutcome.Failure(listOf(textProvider.get("calculator.error.quantity_out_of_range", quantity)))

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
            ?: return CalculationOutcome.Failure(
                listOf(
                    textProvider.get("calculator.error.unknown_paper_type", paperTypeKey),
                    textProvider.get("calculator.error.available_paper_types", prices.products.digitalPrinting.paperType?.keys?.joinToString(", ") ?: "нет доступных")
                )
            )

        val priceTiers = when (printingSides) {
            1 -> paperPrices.oneSided
            2 -> paperPrices.twoSided
            else -> return CalculationOutcome.Failure(listOf(textProvider.get("calculator.error.invalid_printing_sides")))
        }

        val pricePerItem = priceTiers.find { quantity in it.from..it.to }?.pricePerItem
            ?: return CalculationOutcome.Failure(listOf(textProvider.get("calculator.error.quantity_out_of_range", quantity)))

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
        if (validationErrors.isNotEmpty()) return CalculationOutcome.Failure(validationErrors)

        val materialKey = params.originalMaterialKey!!
        val (areaSqm, perimeterM) = calculateDimensions(params)
        val quantity = params.quantity!!

        val materialPricePerSqm = findMaterialPricePerSqm(materialKey)
            ?: return CalculationOutcome.Failure(listOf(textProvider.get("calculator.error.unknown_material_key", materialKey)))

        val totalMaterialPrice = areaSqm * materialPricePerSqm * quantity
        val materialDescription = textProvider.get("material.$materialKey")
        val materialItem = CalculationItem("Материал: $materialDescription", 0.0, totalMaterialPrice)

        val cuttingWorkPrice: Double
        val workItem: CalculationItem

        if (params.thicknessMm != null && params.thicknessMm > 0) {
            val cuttingPricePerMeter = findCuttingPricePerMeter(params.material!!, params.thicknessMm)
                ?: return CalculationOutcome.Failure(listOf(textProvider.get("calculator.error.unknown_operation_key", "$materialDescription ${params.thicknessMm}мм")))
            cuttingWorkPrice = perimeterM * cuttingPricePerMeter * quantity
            workItem = CalculationItem("Работа: Фрезерная резка", cuttingWorkPrice, 0.0)
        } else {
            val plotterPricePerMeter = prices.operations.cutting.plotternaya?.pricePerMeter
                ?: return CalculationOutcome.Failure(listOf("Стоимость плоттерной резки не найдена в прайс-листе."))
            cuttingWorkPrice = perimeterM * plotterPricePerMeter * quantity
            workItem = CalculationItem("Работа: Плоттерная резка", cuttingWorkPrice, 0.0)
        }

        val comments = mutableListOf<String>()
        val preliminaryTotalPrice = totalMaterialPrice + cuttingWorkPrice
        var finalPrice = preliminaryTotalPrice

        val minOrderPrice = prices.operations.generalRules.minOrderOneMachine
        if (preliminaryTotalPrice < minOrderPrice) {
            comments.add(textProvider.get("calculator.comment.min_order_applied", minOrderPrice.toInt()))
            finalPrice = minOrderPrice
        }

        val result = CalculationResult(listOf(materialItem, workItem), cuttingWorkPrice, totalMaterialPrice, finalPrice, comments.ifEmpty { listOf("Расчет выполнен согласно прайс-листу.") })
        return CalculationOutcome.Success(result)
    }

    private fun validateCuttingParams(params: OrderParameters): List<String> {
        val errors = mutableListOf<String>()
        if (params.quantity == null || params.quantity <= 0) errors.add(textProvider.get("calculator.error.no_quantity"))
        if (params.originalMaterialKey.isNullOrBlank()) errors.add("Не выбран материал.")
        val hasRectDimensions = params.widthCm != null && params.heightCm != null && params.widthCm > 0 && params.heightCm > 0
        val hasCircleDimensions = params.diameterCm != null && params.diameterCm > 0
        if (!hasRectDimensions && !hasCircleDimensions) errors.add(textProvider.get("calculator.error.no_dimensions"))
        return errors
    }

    private fun calculateDimensions(params: OrderParameters): Pair<Double, Double> {
        return if (params.diameterCm != null && params.diameterCm > 0) {
            val diameterM = params.diameterCm / 100.0
            val radiusM = diameterM / 2.0
            Pair(kotlin.math.PI * radiusM * radiusM, kotlin.math.PI * diameterM)
        } else {
            val widthM = params.widthCm!! / 100.0
            val heightM = params.heightCm!! / 100.0
            Pair(widthM * heightM, 2 * (widthM + heightM))
        }
    }

    private fun findMaterialPricePerSqm(originalKey: String): Double? {
        val allMaterials = prices.materials.wood + prices.materials.plastic + prices.materials.composite +
                prices.materials.film + prices.materials.magnetic + prices.materials.foam + prices.materials.adhesive
        return allMaterials[originalKey]
    }

    private fun findCuttingPricePerMeter(material: String, thickness: Double): Double? {
        val cuttingPricesByMaterial = prices.operations.cutting.frezernaya.pricesByMaterial
            ?: run {
                logger.error("Структура 'prices_by_material' не найдена в operations.json")
                return null
            }

        val baseMaterialKey = cuttingPricesByMaterial.keys.find { key ->
            material.startsWith(key, ignoreCase = true)
        } ?: run {
            logger.warn("Для материала '$material' не найдено базового ключа в operations.json. Доступные ключи: ${cuttingPricesByMaterial.keys}")
            return null
        }

        val priceTiers = cuttingPricesByMaterial[baseMaterialKey] ?: return null

        val matchingTier = priceTiers.find { tier ->
            thickness >= tier.from && thickness <= tier.to
        }

        return matchingTier?.price
    }

    private fun calculateCuttingAndPrinting(params: OrderParameters): CalculationOutcome {
        val validationErrors = validateCuttingAndPrintingParams(params)
        if (validationErrors.isNotEmpty()) return CalculationOutcome.Failure(validationErrors)

        val materialKey = params.originalMaterialKey!!
        val material = params.material!!
        val printingLayers = params.printingLayers!!
        val (areaSqm, perimeterM) = calculateDimensions(params)
        val quantity = params.quantity!!

        val materialPricePerSqm = findMaterialPricePerSqm(materialKey) ?: return CalculationOutcome.Failure(listOf(textProvider.get("calculator.error.unknown_material_key", materialKey)))
        val totalMaterialPrice = areaSqm * materialPricePerSqm * quantity
        val materialDescription = textProvider.get("material.$materialKey")
        val materialItem = CalculationItem("Материал: $materialDescription", 0.0, totalMaterialPrice)

        val cuttingWorkPrice: Double
        val cuttingItem: CalculationItem
        if (params.thicknessMm != null && params.thicknessMm > 0) {
            val cuttingPricePerMeter = findCuttingPricePerMeter(material, params.thicknessMm)
                ?: return CalculationOutcome.Failure(listOf(textProvider.get("calculator.error.unknown_operation_key", "$materialDescription ${params.thicknessMm}мм")))
            cuttingWorkPrice = perimeterM * cuttingPricePerMeter * quantity
            cuttingItem = CalculationItem("Работа: Фрезерная резка", cuttingWorkPrice, 0.0)
        } else {
            val plotterPricePerMeter = prices.operations.cutting.plotternaya?.pricePerMeter ?: return CalculationOutcome.Failure(listOf("Стоимость плоттерной резки не найдена в прайс-листе."))
            cuttingWorkPrice = perimeterM * plotterPricePerMeter * quantity
            cuttingItem = CalculationItem("Работа: Плоттерная резка", cuttingWorkPrice, 0.0)
        }

        var printingWorkPrice = findUvPrintingPricePerSqm(printingLayers)?.let { pricePerSqm -> areaSqm * pricePerSqm * quantity }
            ?: return CalculationOutcome.Failure(listOf(textProvider.get("calculator.error.unknown_print_operation_key", "УФ-печать $printingLayers слоя")))

        if (material.contains("plywood", ignoreCase = true) || material.contains("mdf", ignoreCase = true)) {
            printingWorkPrice *= prices.operations.printing.uvFlatbed.surcharges.woodSurfaceMultiplier
        }
        val printingItem = CalculationItem("Работа: УФ-печать ($printingLayers слоя)", printingWorkPrice, 0.0)

        val totalWorkPrice = cuttingWorkPrice + printingWorkPrice
        val comments = mutableListOf<String>()
        val preliminaryTotalPrice = totalMaterialPrice + totalWorkPrice
        var finalPrice = preliminaryTotalPrice

        val minOrderPrice = prices.operations.generalRules.minOrderMultiMachine
        if (preliminaryTotalPrice < minOrderPrice) {
            comments.add(textProvider.get("calculator.comment.min_order_multi_applied", minOrderPrice.toInt()))
            finalPrice = minOrderPrice
        }

        val result = CalculationResult(listOf(materialItem, cuttingItem, printingItem), totalWorkPrice, totalMaterialPrice, finalPrice, comments.ifEmpty { listOf("Расчет выполнен согласно прайс-листу.") })
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
}