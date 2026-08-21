package me.chosante.marketserver.service

import me.chosante.common.HarvestDrop
import me.chosante.marketserver.dto.ObservationResponse

/**
 * Expected kamas per action ([value], Σ `drop.dropRate * drop.quantity * latestPrice`) for a list
 * of possible drops -- shared by [HarvestProfitabilityService] and `MonsterFarmingService` (both a
 * harvest node and a monster kill reduce to "roll each independent drop, sum the expected value").
 * [value] is null when none of [drops] have a captured price -- surfaced, not silently treated as
 * 0, same "insufficient_data" honesty as [CraftCostService]. [missingDropCount] surfaces the drops
 * that DID have to be excluded from the sum even when [value] is non-null, so a partial estimate
 * is never mistaken for a complete one (mirrors [CraftCostService]'s `missingPriceCount`).
 */
internal data class ExpectedValue(
    val value: Long?,
    val missingDropCount: Int,
)

internal fun expectedDropValue(
    drops: List<HarvestDrop>,
    prices: Map<Int, ObservationResponse>,
): ExpectedValue {
    val (pricedDrops, unpricedDrops) = drops.partition { prices.containsKey(it.itemId) }
    val value =
        if (pricedDrops.isEmpty()) {
            null
        } else {
            pricedDrops.sumOf { drop -> drop.dropRate * drop.quantity * prices.getValue(drop.itemId).minPrice }.toLong()
        }
    return ExpectedValue(value = value, missingDropCount = unpricedDrops.size)
}
