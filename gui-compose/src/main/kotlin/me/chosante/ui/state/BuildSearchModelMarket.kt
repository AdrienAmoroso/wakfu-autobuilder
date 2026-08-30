package me.chosante.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.chosante.common.Equipment
import me.chosante.common.Rarity
import me.chosante.marketclient.CreateObservationRequest
import me.chosante.marketclient.FlagMotif
import me.chosante.marketclient.UpdatePricesRequest
import me.chosante.ui.components.promptSaveCsvFile
import me.chosante.ui.i18n.Tr
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// [BuildSearchModel]'s Market-screen functions ([Screen.Market]: price-observation search/CRUD,
// craft-cost lookup, item info/sources enrichment, and HDV capture start/stop/poll) -- split out of
// BuildSearchModel.kt, see BuildSearchModelSearch.kt's header.
//
// Every function below follows the same "click -> suspend HTTP call -> update UI state" shape as
// BuildSearchModel.createZenithLink: set a loading state synchronously, launch off the UI thread,
// and land the result (or a market-server-not-running error, indistinguishable from any other
// network failure) back on mainDispatcher.

fun BuildSearchModel.setMarketTab(tab: MarketTab) {
    ui = ui.copy(marketTab = tab)
}

fun BuildSearchModel.setMarketItemIdFilter(value: String) {
    ui = ui.copy(marketItemIdFilter = value.onlyDigits())
}

fun BuildSearchModel.setMarketSearchQuery(value: String) {
    ui = ui.copy(marketSearchQuery = value)
    scheduleMarketSearch()
}

fun BuildSearchModel.setMarketMinLevel(value: String) {
    ui = ui.copy(marketMinLevel = value.onlyDigits())
    scheduleMarketSearch()
}

fun BuildSearchModel.setMarketMaxLevel(value: String) {
    ui = ui.copy(marketMaxLevel = value.onlyDigits())
    scheduleMarketSearch()
}

fun BuildSearchModel.toggleMarketRarityFilter(rarity: Rarity) {
    ui = ui.copy(marketRarityFilter = if (rarity in ui.marketRarityFilter) ui.marketRarityFilter - rarity else ui.marketRarityFilter + rarity)
    scheduleMarketSearch()
}

fun BuildSearchModel.toggleMarketCategoryFilter(category: String) {
    ui =
        ui.copy(
            marketCategoryFilter = if (category in ui.marketCategoryFilter) ui.marketCategoryFilter - category else ui.marketCategoryFilter + category
        )
    scheduleMarketSearch()
}

/** Client-side pagination over the already-fetched [UiState.marketSearchResults] -- clamped so a
 * stale page number (e.g. from a filter that just shrank the result count) can't go out of bounds. */
fun BuildSearchModel.setMarketPage(page: Int) {
    ui = ui.copy(marketPage = clampedPage(page, ui.marketSearchResults.size))
}

/** Debounced (300ms) live search triggered by every filter edit above. */
internal fun BuildSearchModel.scheduleMarketSearch() {
    marketSearchJob?.cancel()
    marketSearchJob =
        scope.launch(Dispatchers.Default) {
            delay(300.milliseconds)
            runMarketSearch()
        }
}

/** Immediate search -- called once on entering the Market screen, see [goToScreen]. */
fun BuildSearchModel.searchMarketItems() {
    marketSearchJob?.cancel()
    scope.launch(Dispatchers.Default) { runMarketSearch() }
}

private suspend fun BuildSearchModel.runMarketSearch() {
    withContext(mainDispatcher) { ui = ui.copy(marketSearchState = MarketState.Loading, error = null) }
    try {
        val results =
            marketRepository.searchItems(
                name = ui.marketSearchQuery.trim().ifBlank { null },
                minLevel = ui.marketMinLevel.toIntOrNull(),
                maxLevel = ui.marketMaxLevel.toIntOrNull(),
                rarities = ui.marketRarityFilter,
                categories = ui.marketCategoryFilter,
                // Without this, market-server's own DEFAULT_SEARCH_LIMIT (50) silently truncated
                // any filtered browse to its 50 lowest-level matches (sorted by level ascending) --
                // e.g. selecting "Equipment" only ever showed level ~0-3 gear. The Prices tab is
                // meant to browse the whole HDV, so every filtered search requests the full catalog.
                limit = FULL_CATALOG_LIMIT
            )
        withContext(mainDispatcher) { ui = ui.copy(marketSearchResults = results, marketSearchState = MarketState.Ready, marketPage = 0) }
    } catch (exception: Exception) {
        withContext(mainDispatcher) {
            ui = ui.copy(marketSearchState = MarketState.Error, error = exception.message ?: "Could not reach market-server")
        }
    }
}

/**
 * Exports every item matching the Prices tab's *current* filters (name/level/rarity/category)
 * as CSV -- not just whatever page is on screen, so the export always matches what the filters
 * describe. A fresh, uncapped-in-practice search (see [FULL_CATALOG_LIMIT]) rather than reusing
 * [UiState.marketSearchResults], which may be a smaller default-view slice.
 */
fun BuildSearchModel.exportItemsToCsv() {
    scope.launch(Dispatchers.Default) {
        val results =
            try {
                marketRepository.searchItems(
                    name = ui.marketSearchQuery.trim().ifBlank { null },
                    minLevel = ui.marketMinLevel.toIntOrNull(),
                    maxLevel = ui.marketMaxLevel.toIntOrNull(),
                    rarities = ui.marketRarityFilter,
                    categories = ui.marketCategoryFilter,
                    limit = FULL_CATALOG_LIMIT
                )
            } catch (exception: Exception) {
                withContext(mainDispatcher) {
                    ui = ui.copy(error = exception.message ?: "Could not reach market-server")
                }
                return@launch
            }
        val csv = itemsToCsv(results)
        withContext(mainDispatcher) {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            val file = promptSaveCsvFile(ownerWindow, suggestedFileName = "wakfu-items-$timestamp.csv")
            if (file != null) {
                file.writeText(csv, Charsets.UTF_8)
                ui = ui.copy(toast = Tr.TOAST_ITEMS_EXPORTED.value(ui.lang))
            }
        }
    }
}

/** Expands/collapses [itemId]'s full price-observation history under its row in the browse list. */
fun BuildSearchModel.toggleExpandedMarketItem(itemId: Int) {
    if (ui.marketExpandedItemId == itemId) {
        ui = ui.copy(marketExpandedItemId = null)
        return
    }
    // Clear the previous item's rows immediately -- otherwise, until the fetch below lands, they'd
    // render under the *new* item's header (same ui.marketObservations backs every expanded card),
    // and a Save/Delete click in that window would silently mutate the wrong item's data.
    ui = ui.copy(marketExpandedItemId = itemId, marketItemIdFilter = itemId.toString(), marketObservations = emptyList())
    loadMarketObservations()
}

fun BuildSearchModel.setCraftCostItemId(value: String) {
    ui = ui.copy(craftCostItemId = value.onlyDigits())
}

fun BuildSearchModel.loadMarketObservations() {
    ui = ui.copy(marketLoadState = MarketState.Loading, error = null)
    val itemId = ui.marketItemIdFilter.toIntOrNull()
    scope.launch(Dispatchers.Default) {
        try {
            val observations = marketRepository.listObservations(itemId = itemId)
            withContext(mainDispatcher) {
                ui = ui.copy(marketObservations = observations, marketLoadState = MarketState.Ready)
            }
        } catch (exception: Exception) {
            withContext(mainDispatcher) {
                ui = ui.copy(marketLoadState = MarketState.Error, error = exception.message ?: "Could not reach market-server")
            }
        }
    }
}

fun BuildSearchModel.createMarketObservation(request: CreateObservationRequest) {
    ui = ui.copy(marketLoadState = MarketState.Loading, error = null)
    scope.launch(Dispatchers.Default) {
        try {
            marketRepository.createObservation(request)
            val observations = marketRepository.listObservations(itemId = ui.marketItemIdFilter.toIntOrNull())
            withContext(mainDispatcher) {
                ui = ui.copy(marketObservations = observations, marketLoadState = MarketState.Ready, toast = "Observation created")
            }
            searchMarketItems()
        } catch (exception: Exception) {
            withContext(mainDispatcher) {
                ui = ui.copy(marketLoadState = MarketState.Error, error = exception.message ?: "Could not create observation")
            }
        }
    }
}

fun BuildSearchModel.deleteMarketObservation(id: Int) {
    scope.launch(Dispatchers.Default) {
        try {
            marketRepository.deleteObservation(id)
            val observations = marketRepository.listObservations(itemId = ui.marketItemIdFilter.toIntOrNull())
            withContext(mainDispatcher) { ui = ui.copy(marketObservations = observations) }
            searchMarketItems()
        } catch (exception: Exception) {
            withContext(mainDispatcher) { ui = ui.copy(error = exception.message ?: "Could not delete observation") }
        }
    }
}

fun BuildSearchModel.updateMarketPrices(
    id: Int,
    minPrice: Long,
    avgPrice: Long,
    medianPrice: Long?,
) {
    scope.launch(Dispatchers.Default) {
        try {
            val updated = marketRepository.updatePrices(id, UpdatePricesRequest(minPrice, avgPrice, medianPrice))
            withContext(mainDispatcher) {
                ui = ui.copy(marketObservations = ui.marketObservations.map { if (it.id == id) updated else it })
            }
            searchMarketItems()
        } catch (exception: Exception) {
            withContext(mainDispatcher) { ui = ui.copy(error = exception.message ?: "Could not update prices") }
        }
    }
}

fun BuildSearchModel.setMarketFlag(
    id: Int,
    motif: FlagMotif,
) {
    scope.launch(Dispatchers.Default) {
        try {
            val updated = marketRepository.setFlag(id, motif)
            withContext(mainDispatcher) {
                ui = ui.copy(marketObservations = ui.marketObservations.map { if (it.id == id) updated else it })
            }
        } catch (exception: Exception) {
            withContext(mainDispatcher) { ui = ui.copy(error = exception.message ?: "Could not flag observation") }
        }
    }
}

fun BuildSearchModel.lookupCraftCost() {
    val itemId = ui.craftCostItemId.toIntOrNull() ?: return
    ui = ui.copy(craftCostState = MarketState.Loading, craftCostResult = null, error = null)
    scope.launch(Dispatchers.Default) {
        try {
            val result = marketRepository.craftCost(itemId)
            withContext(mainDispatcher) {
                ui = ui.copy(craftCostResult = result, craftCostState = MarketState.Ready)
            }
        } catch (exception: Exception) {
            withContext(mainDispatcher) {
                ui = ui.copy(craftCostState = MarketState.Error, error = exception.message ?: "Could not compute craft cost")
            }
        }
    }
}

/**
 * Resolves [itemId] into the rich [me.chosante.common.Equipment] display shown next to prices/
 * ingredients (icon, rarity, localized name, level) via `GET /api/items/{id}`, caching the
 * result in [UiState.itemInfoCache]. Fire-and-forget and idempotent per session: a row's
 * `LaunchedEffect` calls this on every recomposition, but [UiState.itemInfoRequested] makes
 * every id after the first a no-op instead of re-fetching. A miss (unknown id, or market-server
 * unreachable) simply leaves the id out of the cache -- callers fall back to showing the bare
 * itemId, never an error banner, since this is cosmetic enrichment only.
 */
fun BuildSearchModel.ensureItemInfoLoaded(itemId: Int) {
    if (itemId in ui.itemInfoRequested) return
    ui = ui.copy(itemInfoRequested = ui.itemInfoRequested + itemId)
    scope.launch(Dispatchers.Default) {
        val equipment = marketRepository.getItem(itemId)
        if (equipment != null) {
            withContext(mainDispatcher) {
                ui = ui.copy(itemInfoCache = ui.itemInfoCache + (itemId to equipment))
            }
        }
    }
}

/**
 * Resolves [itemId]'s "how do I get this" sources (recipe + monster/harvest-node drops) via
 * `GET /api/items/{id}/sources`, caching the result in [UiState.itemSourcesCache]. Same
 * fire-and-forget/idempotent-per-session shape as [ensureItemInfoLoaded].
 */
fun BuildSearchModel.ensureItemSourcesLoaded(itemId: Int) {
    if (itemId in ui.itemSourcesRequested) return
    ui = ui.copy(itemSourcesRequested = ui.itemSourcesRequested + itemId)
    scope.launch(Dispatchers.Default) {
        val sources = marketRepository.itemSources(itemId)
        if (sources != null) {
            withContext(mainDispatcher) {
                ui = ui.copy(itemSourcesCache = ui.itemSourcesCache + (itemId to sources))
            }
        }
    }
}

/** Start an HDV price capture (kills any running Wakfu, launches tshark + the real game). */
fun BuildSearchModel.startCapture() {
    scope.launch(Dispatchers.Default) {
        try {
            val status = marketRepository.startCapture()
            withContext(mainDispatcher) { ui = ui.copy(captureStatus = status) }
            if (status.phase == "capturing") pollCaptureStatusUntilTerminal()
        } catch (exception: Exception) {
            withContext(mainDispatcher) { ui = ui.copy(error = exception.message ?: "Could not start capture") }
        }
    }
}

/** Stop the running capture; market-server kills tshark and parses+imports what was captured. */
fun BuildSearchModel.stopCapture() {
    scope.launch(Dispatchers.Default) {
        try {
            val status = marketRepository.stopCapture()
            withContext(mainDispatcher) { ui = ui.copy(captureStatus = status) }
            if (status.phase == "processing") pollCaptureStatusUntilTerminal()
        } catch (exception: Exception) {
            withContext(mainDispatcher) { ui = ui.copy(error = exception.message ?: "Could not stop capture") }
        }
    }
}

/** One-shot status fetch -- called when the Market screen is (re)opened, see [goToScreen]. */
fun BuildSearchModel.refreshCaptureStatus() {
    scope.launch(Dispatchers.Default) {
        try {
            val status = marketRepository.captureStatus()
            withContext(mainDispatcher) { ui = ui.copy(captureStatus = status) }
            if (status.phase == "capturing" || status.phase == "processing") pollCaptureStatusUntilTerminal()
        } catch (exception: Exception) {
            withContext(mainDispatcher) { ui = ui.copy(error = exception.message ?: "Could not reach market-server") }
        }
    }
}

/** Polls every 3s while capturing/processing; stops itself once idle/error and refreshes the Prices table. */
internal fun BuildSearchModel.pollCaptureStatusUntilTerminal() {
    captureJob?.cancel()
    captureJob =
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(3.seconds)
                val status =
                    try {
                        marketRepository.captureStatus()
                    } catch (exception: Exception) {
                        withContext(mainDispatcher) {
                            ui = ui.copy(error = exception.message ?: "Could not reach market-server")
                        }
                        return@launch
                    }
                withContext(mainDispatcher) { ui = ui.copy(captureStatus = status) }
                if (status.phase != "capturing" && status.phase != "processing") {
                    loadMarketObservations()
                    return@launch
                }
            }
        }
}
