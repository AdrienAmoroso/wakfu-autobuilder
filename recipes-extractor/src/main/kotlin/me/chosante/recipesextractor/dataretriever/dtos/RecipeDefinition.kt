package me.chosante.recipesextractor.dataretriever.dtos

import kotlinx.serialization.Serializable

@Serializable
data class RecipeDefinition(
    val id: Int,
    val categoryId: Int,
    val level: Int,
    val xpRatio: Int,
    val isUpgrade: Boolean,
    val upgradeItemId: Int,
)
