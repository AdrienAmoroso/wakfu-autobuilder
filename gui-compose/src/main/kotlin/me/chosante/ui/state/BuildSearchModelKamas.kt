package me.chosante.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// [BuildSearchModel]'s Kamas-screen functions ([Screen.Kamas]: the crafting/harvesting/monster-drop
// profitability scanners) -- split out of BuildSearchModel.kt, see BuildSearchModelSearch.kt's header.

/** Same clamped client-side pagination as [setMarketPage], for the Kamas screen's three tabs. */
fun BuildSearchModel.setKamasCraftPage(page: Int) {
    ui = ui.copy(kamasCraftPage = clampedPage(page, ui.craftOpportunities.size))
}

fun BuildSearchModel.setKamasHarvestPage(page: Int) {
    ui = ui.copy(kamasHarvestPage = clampedPage(page, ui.harvestOpportunities.size))
}

fun BuildSearchModel.setKamasMonsterPage(page: Int) {
    ui = ui.copy(kamasMonsterPage = clampedPage(page, ui.monsterFarmingOpportunities.size))
}

// --- Kamas (money-making opportunity finder) ---

fun BuildSearchModel.setKamasTab(tab: KamasTab) {
    ui = ui.copy(kamasTab = tab)
    ensureKamasTabLoaded(tab)
}

/** Fetches [tab]'s ranking on first display only -- called on tab switch and on entering the Kamas screen. */
internal fun BuildSearchModel.ensureKamasTabLoaded(tab: KamasTab) {
    when (tab) {
        KamasTab.CRAFTING -> if (ui.craftOpportunities.isEmpty() && ui.craftOpportunitiesState == MarketState.Idle) scanCraftOpportunities()
        KamasTab.HARVESTING -> if (ui.harvestOpportunities.isEmpty() && ui.harvestOpportunitiesState == MarketState.Idle) scanHarvestOpportunities()
        KamasTab.MONSTER_FARMING ->
            if (ui.monsterFarmingOpportunities.isEmpty() && ui.monsterFarmingOpportunitiesState == MarketState.Idle) scanMonsterFarmingOpportunities()
    }
}

/** Scans every recipe with enough captured price data, ranked by ROI -- the Kamas screen's Crafting tab. */
fun BuildSearchModel.scanCraftOpportunities() {
    ui = ui.copy(craftOpportunitiesState = MarketState.Loading, error = null)
    scope.launch(Dispatchers.Default) {
        try {
            val results = marketRepository.craftOpportunities(limit = FULL_CATALOG_LIMIT)
            withContext(mainDispatcher) {
                ui = ui.copy(craftOpportunities = results, craftOpportunitiesState = MarketState.Ready, kamasCraftPage = 0)
            }
        } catch (exception: Exception) {
            withContext(mainDispatcher) {
                ui = ui.copy(craftOpportunitiesState = MarketState.Error, error = exception.message ?: "Could not reach market-server")
            }
        }
    }
}

/** Scans every harvest node with enough captured drop-price data, ranked by expected kamas per harvest. */
fun BuildSearchModel.scanHarvestOpportunities() {
    ui = ui.copy(harvestOpportunitiesState = MarketState.Loading, error = null)
    scope.launch(Dispatchers.Default) {
        try {
            val results = marketRepository.harvestOpportunities(limit = FULL_CATALOG_LIMIT)
            withContext(mainDispatcher) {
                ui = ui.copy(harvestOpportunities = results, harvestOpportunitiesState = MarketState.Ready, kamasHarvestPage = 0)
            }
        } catch (exception: Exception) {
            withContext(mainDispatcher) {
                ui = ui.copy(harvestOpportunitiesState = MarketState.Error, error = exception.message ?: "Could not reach market-server")
            }
        }
    }
}

/** Scans every monster with a known drop table, ranked by expected kamas per kill. */
fun BuildSearchModel.scanMonsterFarmingOpportunities() {
    ui = ui.copy(monsterFarmingOpportunitiesState = MarketState.Loading, error = null)
    scope.launch(Dispatchers.Default) {
        try {
            val results = marketRepository.monsterFarmingOpportunities(limit = FULL_CATALOG_LIMIT)
            withContext(mainDispatcher) {
                ui = ui.copy(monsterFarmingOpportunities = results, monsterFarmingOpportunitiesState = MarketState.Ready, kamasMonsterPage = 0)
            }
        } catch (exception: Exception) {
            withContext(mainDispatcher) {
                ui = ui.copy(monsterFarmingOpportunitiesState = MarketState.Error, error = exception.message ?: "Could not reach market-server")
            }
        }
    }
}
