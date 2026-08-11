package me.chosante.recipesextractor.dataretriever

import me.chosante.recipesextractor.dataretriever.dtos.Jobs
import me.chosante.recipesextractor.dataretriever.dtos.RecipeDefinition
import me.chosante.recipesextractor.dataretriever.dtos.RecipeIngredientDto
import me.chosante.recipesextractor.dataretriever.dtos.RecipeResult

data class WakfuData(
    val recipes: List<RecipeDefinition>,
    val recipeResults: List<RecipeResult>,
    val recipeIngredients: List<RecipeIngredientDto>,
    val jobs: List<Jobs>,
)
