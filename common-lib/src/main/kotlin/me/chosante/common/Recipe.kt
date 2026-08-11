package me.chosante.common

import kotlinx.serialization.Serializable

@Serializable
data class RecipeIngredient(
    val itemId: Int,
    val quantity: Int,
)

@Serializable
data class Recipe(
    val recipeId: Int,
    // The CRAFTED (output) item, not an ingredient.
    val itemId: Int,
    val resultQuantity: Int,
    val jobId: Int,
    val jobName: I18nText,
    val level: Int,
    val ingredients: List<RecipeIngredient>,
)
