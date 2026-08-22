package me.chosante.marketserver.routes

import io.ktor.http.Parameters

private const val DEFAULT_SCAN_LIMIT = 50

// Comfortably covers the largest of the three scans -- Monster Farming now returns every monster
// in the game (2846, see MonsterDropCatalog's doc comment), not just ones with known drops.
private const val MAX_SCAN_LIMIT = 5_000

/**
 * Shared `?limit=` parsing for the Kamas screen's three "opportunities" scan routes (crafting/
 * harvesting/monster farming). `coerceIn` also rejects a caller-supplied zero/negative value
 * (clamped up to 1) instead of passing it straight to `scanAll`'s `take(limit)`, which would
 * silently return an empty list for 0 or throw for negative.
 */
internal fun Parameters.resolveScanLimit(): Int = (this["limit"]?.toIntOrNull() ?: DEFAULT_SCAN_LIMIT).coerceIn(1, MAX_SCAN_LIMIT)
