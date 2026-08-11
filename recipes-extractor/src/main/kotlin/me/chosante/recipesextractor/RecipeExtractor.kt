package me.chosante.recipesextractor

import me.chosante.common.I18nText
import me.chosante.common.Recipe
import me.chosante.common.RecipeIngredient
import me.chosante.recipesextractor.dataretriever.WakfuData

fun extractData(wakfuData: WakfuData): List<Recipe> {
    val resultByRecipeId = wakfuData.recipeResults.associateBy { it.recipeId }
    val ingredientsByRecipeId = wakfuData.recipeIngredients.groupBy { it.recipeId }
    val jobNameByCategoryId = wakfuData.jobs.associate { it.definition.id to it.title }

    return wakfuData.recipes.mapNotNull { recipe ->
        val result = resultByRecipeId[recipe.id] ?: return@mapNotNull null
        val title = jobNameByCategoryId[recipe.categoryId]
        Recipe(
            recipeId = recipe.id,
            itemId = result.productedItemId,
            resultQuantity = result.productedItemQuantity,
            jobId = recipe.categoryId,
            jobName =
                title?.let { I18nText(fr = it.fr, en = it.en, es = it.es, pt = it.pt) }
                    ?: I18nText(fr = "", en = "", es = "", pt = ""),
            level = recipe.level,
            ingredients =
                ingredientsByRecipeId[recipe.id]?.map { RecipeIngredient(itemId = it.itemId, quantity = it.quantity) }
                    ?: emptyList()
        )
    }
}
