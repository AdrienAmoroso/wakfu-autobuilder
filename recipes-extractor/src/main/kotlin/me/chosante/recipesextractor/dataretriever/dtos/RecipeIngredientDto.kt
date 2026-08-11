package me.chosante.recipesextractor.dataretriever.dtos

import kotlinx.serialization.Serializable

@Serializable
data class RecipeIngredientDto(
    val recipeId: Int,
    val itemId: Int,
    val quantity: Int,
    val ingredientOrder: Int,
)
