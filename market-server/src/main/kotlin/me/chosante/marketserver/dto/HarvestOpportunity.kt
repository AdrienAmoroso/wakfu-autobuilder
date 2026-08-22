package me.chosante.marketserver.dto

import kotlinx.serialization.Serializable
import me.chosante.common.HarvestNode

/**
 * The Kamas screen's Harvesting tab: a [node] plus its expected kamas value per harvest ([Σ]
 * `drop.dropRate * drop.quantity * latestPrice`, see `HarvestProfitabilityService`). [expectedValue]
 * is null when none of its drops have a captured price -- surfaced, not hidden, same
 * "insufficient_data" honesty as `CraftCostResponse`. [missingDropCount] > 0 means [expectedValue],
 * even when non-null, is a partial sum -- some of this node's drops had no captured price and were
 * excluded rather than silently counted as worthless. [drops] is the same drop table as
 * [node.drops][HarvestNode.drops], priced per-row -- the expand-in-place detail behind the
 * aggregate fields above (see [DropInfo]).
 */
@Serializable
data class HarvestOpportunity(
    val node: HarvestNode,
    val expectedValue: Long?,
    val missingDropCount: Int,
    val drops: List<DropInfo> = emptyList(),
)
