package me.chosante.recipesextractor.dataretriever.dtos

import kotlinx.serialization.Serializable

@Serializable
data class RecipeResult(
    val recipeId: Int,
    val productedItemId: Int,
    val productOrder: Int,
    val productedItemQuantity: Int,
)
