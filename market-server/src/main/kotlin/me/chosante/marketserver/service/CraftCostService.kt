package me.chosante.marketserver.service

import me.chosante.common.Recipe
import me.chosante.marketserver.dto.CraftCostResponse
import me.chosante.marketserver.dto.IngredientCost
import me.chosante.marketserver.dto.ObservationResponse
import me.chosante.marketserver.equipment.RecipeCatalog
import org.jetbrains.exposed.v1.jdbc.Database

private const val DEFAULT_TAX_RATE = 0.02
private const val ROI_CRAFT_THRESHOLD = 0.05

// Deliberately non-recursive, matching the old WakfuMarket.App's CraftCostService: an ingredient
// that is itself craftable is still priced from its own latest market observation, never by
// resolving its own recipe. Informational only -- never fed into the CP-SAT solver's objective.
object CraftCostService {
    fun compute(
        db: Database,
        itemId: Int,
        server: String?,
        taxRate: Double?,
    ): CraftCostResponse? {
        val recipe = RecipeCatalog.findByItemId(itemId) ?: return null
        val neededIds = recipe.ingredients.map { it.itemId }.toSet() + itemId
        val prices = PriceObservationService.latestForItems(db, neededIds, server)
        return scoreRecipe(recipe, prices, taxRate ?: DEFAULT_TAX_RATE)
    }

    /**
     * Scans every known recipe and ranks the ones with enough price data to score, best ROI
     * first -- the Kamas screen's "Crafting" tab. One batched price lookup for every ingredient +
     * result id across ALL recipes (not one lookup per recipe per ingredient, which would be
     * thousands of individual queries across ~5600 recipes).
     */
    fun scanAll(
        db: Database,
        server: String?,
        taxRate: Double?,
        limit: Int,
    ): List<CraftCostResponse> {
        val recipes = RecipeCatalog.all()
        val neededIds =
            recipes
                .flatMapTo(mutableSetOf()) { it.ingredients.map { ingredient -> ingredient.itemId } }
                .apply { addAll(recipes.map { it.itemId }) }
        val prices = PriceObservationService.latestForItems(db, neededIds, server)
        val effectiveTaxRate = taxRate ?: DEFAULT_TAX_RATE
        return recipes
            .asSequence()
            .map { scoreRecipe(it, prices, effectiveTaxRate) }
            .filter { it.decision != "insufficient_data" }
            .sortedByDescending { it.roi }
            .take(limit)
            .toList()
    }

    private fun scoreRecipe(
        recipe: Recipe,
        prices: Map<Int, ObservationResponse>,
        taxRate: Double,
    ): CraftCostResponse {
        val perIngredient =
            recipe.ingredients.map { ingredient ->
                val observation = prices[ingredient.itemId]
                val unitPrice = observation?.minPrice
                val cost =
                    IngredientCost(
                        itemId = ingredient.itemId,
                        quantity = ingredient.quantity,
                        unitPrice = unitPrice,
                        subtotal = unitPrice?.let { it * ingredient.quantity }
                    )
                cost to observation?.confidenceScore
            }
        val ingredientCosts = perIngredient.map { it.first }
        val missingPriceCount = ingredientCosts.count { it.unitPrice == null }
        val craftCost = ingredientCosts.sumOf { it.subtotal ?: 0L }

        val marketPrice = prices[recipe.itemId]?.minPrice
        val grossMargin = marketPrice?.let { it - craftCost }
        val netMargin = grossMargin?.let { (it * (1 - taxRate)).toLong() }
        val roi = netMargin?.takeIf { craftCost > 0 }?.let { it.toDouble() / craftCost }

        val availableConfidences = perIngredient.mapNotNull { it.second }
        val avgConfidence = if (availableConfidences.isEmpty()) 0.0 else availableConfidences.average()
        val completenessFactor =
            if (recipe.ingredients.isEmpty()) 0.0 else (recipe.ingredients.size - missingPriceCount).toDouble() / recipe.ingredients.size
        val confidence = avgConfidence * completenessFactor

        val decision =
            when {
                missingPriceCount > 0 || marketPrice == null -> "insufficient_data"
                roi != null && roi > ROI_CRAFT_THRESHOLD -> "craft"
                else -> "buy"
            }

        return CraftCostResponse(
            itemId = recipe.itemId,
            craftCost = craftCost,
            marketPrice = marketPrice,
            grossMargin = grossMargin,
            netMargin = netMargin,
            roi = roi,
            confidence = confidence,
            missingPriceCount = missingPriceCount,
            decision = decision,
            ingredients = ingredientCosts
        )
    }
}
