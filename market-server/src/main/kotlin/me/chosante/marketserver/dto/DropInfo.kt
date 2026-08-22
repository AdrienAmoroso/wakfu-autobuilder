package me.chosante.marketserver.dto

import kotlinx.serialization.Serializable

/**
 * One priced row of a [HarvestOpportunity]/[MonsterFarmingOpportunity]'s drop table -- the
 * expand-in-place detail behind those rows' aggregate `expectedValue`/`missingDropCount`, the same
 * "aggregate + per-line detail" shape [CraftCostResponse] already uses for its `ingredients`.
 * [unitPrice] null means this drop has no captured price (excluded from the aggregate sum, never
 * silently counted as worthless).
 */
@Serializable
data class DropInfo(
    val itemId: Int,
    val dropRate: Double,
    val quantity: Int,
    val unitPrice: Long? = null,
)
