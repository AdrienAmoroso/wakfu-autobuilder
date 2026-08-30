package me.chosante.marketserver.service

import me.chosante.marketserver.dto.HarvestDropSource
import me.chosante.marketserver.dto.ItemSourcesResponse
import me.chosante.marketserver.dto.MonsterDropSource
import me.chosante.marketserver.dto.RecipeSummary
import me.chosante.marketserver.equipment.HarvestCatalog
import me.chosante.marketserver.equipment.MonsterDropCatalog
import me.chosante.marketserver.equipment.RecipeCatalog

/**
 * "How do I get this item" -- pure, no DB/pricing (see [ItemSourcesResponse]'s doc comment for why),
 * so this is a simple, fast, always-fresh lookup across the 3 catalogs that already answer each
 * half: [RecipeCatalog] (craft), [MonsterDropCatalog.monstersByItemId] and
 * [HarvestCatalog.nodesByItemId] (the two reverse indices this service exists to make use of).
 */
object ItemSourcesService {
    fun compute(itemId: Int): ItemSourcesResponse {
        val recipe =
            RecipeCatalog.findByItemId(itemId)?.let { r ->
                RecipeSummary(
                    recipeId = r.recipeId,
                    jobName = r.jobName,
                    level = r.level,
                    resultQuantity = r.resultQuantity,
                    ingredients = r.ingredients
                )
            }

        val monsterSources =
            MonsterDropCatalog.monstersByItemId[itemId].orEmpty().map { (monster, drop) ->
                MonsterDropSource(
                    monsterId = monster.id,
                    name = monster.name,
                    level = monster.level,
                    isBoss = monster.isBoss,
                    gfx = monster.gfx,
                    dropRate = drop.dropRate,
                    quantity = drop.quantity
                )
            }

        val harvestSources =
            HarvestCatalog.nodesByItemId[itemId].orEmpty().map { (node, drop) ->
                HarvestDropSource(
                    resourceId = node.resourceId,
                    name = node.name,
                    category = node.category,
                    skillLevelRequired = node.skillLevelRequired,
                    dropRate = drop.dropRate,
                    quantity = drop.quantity
                )
            }

        return ItemSourcesResponse(
            itemId = itemId,
            recipe = recipe,
            monsterSources = monsterSources,
            harvestSources = harvestSources
        )
    }
}
