package me.chosante.marketserver.dto

import kotlinx.serialization.Serializable
import me.chosante.common.I18nText
import me.chosante.common.RecipeIngredient

/**
 * A recipe's identity/ingredients, with no price data -- deliberately lighter than
 * [CraftCostResponse], which needs a DB price lookup (`server`/`taxRate`) that would make
 * [me.chosante.marketserver.service.ItemSourcesService]'s "just tell me the recipe" lookup slow and
 * price-dependent. A caller that also wants cost/margin/ROI can hit `/api/crafts/{itemId}/cost`
 * separately (unchanged).
 */
@Serializable
data class RecipeSummary(
    val recipeId: Int,
    val jobName: I18nText,
    val level: Int,
    val resultQuantity: Int,
    val ingredients: List<RecipeIngredient>,
)

/** One monster that can drop the item, from [me.chosante.marketserver.equipment.MonsterDropCatalog.monstersByItemId]. */
@Serializable
data class MonsterDropSource(
    val monsterId: Int,
    val name: I18nText,
    val level: Int,
    val isBoss: Boolean,
    val gfx: Int? = null,
    val dropRate: Double,
    val quantity: Int,
)

/** One harvest node that can yield the item, from [me.chosante.marketserver.equipment.HarvestCatalog.nodesByItemId]. */
@Serializable
data class HarvestDropSource(
    val resourceId: Int,
    val name: I18nText,
    val category: String,
    val skillLevelRequired: Int,
    val dropRate: Double,
    val quantity: Int,
)

/**
 * "How do I get this item" -- the foundation for a future full build-explanation page (not built
 * yet; this is the data layer + a minimal surfacing in the existing item-detail popup). An item can
 * legitimately have none, one, or several of these at once (e.g. both craftable AND a monster drop).
 */
@Serializable
data class ItemSourcesResponse(
    val itemId: Int,
    val recipe: RecipeSummary? = null,
    val monsterSources: List<MonsterDropSource> = emptyList(),
    val harvestSources: List<HarvestDropSource> = emptyList(),
)
