package org.example.calculation.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class TierPrice(
    val from: Int,
    val to: Int,
    @SerialName("price_per_item") val pricePerItem: Double
)


@Serializable
data class ProductPriceList(
    val badges: ProductGroup,
    @SerialName("digital_printing") val digitalPrinting: ProductGroup
)

@Serializable
data class ProductGroup(
    val comment: String,
    val types: Map<String, List<TierPrice>>? = null,
    @SerialName("paper_type") val paperType: Map<String, PaperPrices>? = null
)

@Serializable
data class PaperPrices(
    @SerialName("односторонняя_4+0") val oneSided: List<TierPrice>,
    @SerialName("двухсторонняя_4+4") val twoSided: List<TierPrice>
)


@Serializable
data class MaterialPriceList(
    val comment: String,
    val wood: Map<String, Double>,
    val plastic: Map<String, Double>,
    val composite: Map<String, Double>,
    val film: Map<String, Double>,
    val magnetic: Map<String, Double>,
    val foam: Map<String, Double>,
    val adhesive: Map<String, Double>
)


@Serializable
data class OperationPriceList(
    val comment: String,
    @SerialName("general_rules") val generalRules: GeneralRules,
    val cutting: CuttingOperations,
    val printing: PrintingOperations
)

@Serializable
data class GeneralRules(
    @SerialName("min_order_one_machine") val minOrderOneMachine: Double,
    @SerialName("min_order_multi_machine") val minOrderMultiMachine: Double
)

@Serializable
data class CuttingOperations(
    val frezernaya: CuttingType,
    val plotternaya: PlotterCutting? = null
)

@Serializable
data class CuttingPriceTier(
    val from: Double,
    val to: Double,
    val price: Double
)

@Serializable
data class CuttingType(
    val comment: String,
    @SerialName("prices_by_material") val pricesByMaterial: Map<String, List<CuttingPriceTier>>? = null
)

@Serializable
data class PrintingOperations(
    @SerialName("uv_flatbed") val uvFlatbed: UvPrinting
)

@Serializable
data class UvPrinting(
    val comment: String,
    val prices: Map<String, Double>,
    val surcharges: Surcharges
)

@Serializable
data class Surcharges(
    @SerialName("wood_surface_multiplier") val woodSurfaceMultiplier: Double
)

@Serializable
data class OrderParameters(
    @SerialName("original_material_key") val originalMaterialKey: String? = null,
    @SerialName("product_type") val productType: String,
    val shape: String? = null,
    val size: String? = null,
    val quantity: Int? = null,
    val material: String? = null,
    @SerialName("thickness_mm") val thicknessMm: Double? = null,
    @SerialName("width_cm") val widthCm: Double? = null,
    @SerialName("height_cm") val heightCm: Double? = null,
    @SerialName("diameter_cm") val diameterCm: Double? = null,
    @SerialName("printing_layers") val printingLayers: Int? = null,
    @SerialName("printing_sides") val printingSides: Int? = null
)

@Serializable
data class PlotterCutting(
    val comment: String,
    @SerialName("price_per_meter") val pricePerMeter: Double
)

/**
 * Результат расчета
 */
data class CalculationResult(
    val items: List<CalculationItem>,
    val totalWorkPrice: Double,
    val totalMaterialPrice: Double,
    val finalTotalPrice: Double,
    val comments: List<String>
)

/**
 * Детализация расчета по одной позиции
 */
data class CalculationItem(
    val description: String,
    val workPrice: Double,
    val materialPrice: Double
)

/**
 * Запечатанный класс для представления результата вычислений.
 * Позволяет статически определить, был ли расчет успешным или произошла ошибка.
 */
sealed class CalculationOutcome {
    data class Success(val result: CalculationResult) : CalculationOutcome()

    data class Failure(val errors: List<String>) : CalculationOutcome()
}