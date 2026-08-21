package me.chosante.marketserver.dto

import kotlinx.serialization.Serializable
import me.chosante.common.Monster

/**
 * The Kamas screen's Monster Farming tab: a [monster] plus its expected kamas value per kill, same
 * "Σ dropRate * quantity * latestPrice" formula as [HarvestOpportunity] (see
 * `ExpectedValueCalculator.kt`). [expectedValue] is null when none of its drops have a captured
 * price. [missingDropCount] > 0 means [expectedValue], even when non-null, is a partial sum -- see
 * [HarvestOpportunity].
 */
@Serializable
data class MonsterFarmingOpportunity(
    val monster: Monster,
    val expectedValue: Long?,
    val missingDropCount: Int,
)
