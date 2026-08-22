package me.chosante.marketserver.dto

import kotlinx.serialization.Serializable
import me.chosante.common.Monster

/**
 * The Kamas screen's Monster Farming tab: a [monster] plus its expected kamas value per kill, same
 * "Σ dropRate * quantity * latestPrice" formula as [HarvestOpportunity] (see
 * `ExpectedValueCalculator.kt`). [expectedValue] is null when none of its drops have a captured
 * price. [missingDropCount] > 0 means [expectedValue], even when non-null, is a partial sum -- see
 * [HarvestOpportunity]. [totalDropCount] == 0 is a DIFFERENT thing from a fully-priced monster
 * ([missingDropCount] == 0 too, in that case): it means this monster has no known drop table at
 * all (`MonsterDropCatalog` now includes every monster, not just ones with drops -- see its doc
 * comment), not that every known drop happens to be priced. [Monster] carries no drop list of its
 * own (unlike [HarvestOpportunity.node]), so this field is the only way the GUI can tell those two
 * apart. [drops] is that drop table, priced per-row -- the expand-in-place detail behind the
 * aggregate fields above (see [DropInfo]); its size always equals [totalDropCount].
 */
@Serializable
data class MonsterFarmingOpportunity(
    val monster: Monster,
    val expectedValue: Long?,
    val missingDropCount: Int,
    val totalDropCount: Int,
    val drops: List<DropInfo> = emptyList(),
)
