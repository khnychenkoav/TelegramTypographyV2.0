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
    val types: Map<String, List<TierPrice>>? = null, // Для значков
    @SerialName("paper_type") val paperType: Map<String, PaperPrices>? = null // Для цифровой печати
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
    val frezernaya: CuttingType
)

@Serializable
data class CuttingType(
    val comment: String,
    val prices: Map<String, Double>
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