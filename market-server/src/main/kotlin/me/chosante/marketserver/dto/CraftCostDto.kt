package me.chosante.marketserver.dto

import kotlinx.serialization.Serializable
import me.chosante.common.I18nText

@Serializable
data class IngredientCost(
    val itemId: Int,
    val quantity: Int,
    val unitPrice: Long? = null,
    val subtotal: Long? = null,
)

@Serializable
data class CraftCostResponse(
    val itemId: Int,
    val jobName: I18nText,
    val craftCost: Long? = null,
    val marketPrice: Long? = null,
    val grossMargin: Long? = null,
    val netMargin: Long? = null,
    val roi: Double? = null,
    val confidence: Double,
    val missingPriceCount: Int,
    val decision: String,
    val ingredients: List<IngredientCost>,
)
