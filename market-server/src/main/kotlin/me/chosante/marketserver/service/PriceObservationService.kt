package me.chosante.marketserver.service

import me.chosante.marketserver.db.PriceObservations
import me.chosante.marketserver.dto.CreateObservationRequest
import me.chosante.marketserver.dto.FlagMotif
import me.chosante.marketserver.dto.ObservationResponse
import me.chosante.marketserver.dto.UpdatePricesRequest
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

private const val MAX_LIMIT = 200
private const val DEFAULT_LIMIT = 50

// Every function takes the Database explicitly and calls transaction(db) { ... }, not the no-arg
// transaction { ... } form -- the latter relies on Exposed's JVM-global "current default
// database", which flaps when several Application instances (e.g. one per test) each connect to
// their own SQLite file within the same JVM, causing rare cross-instance query leakage.
object PriceObservationService {
    fun listObservations(
        db: Database,
        itemId: Int?,
        limit: Int?,
    ): List<ObservationResponse> =
        transaction(db) {
            val query =
                if (itemId != null) {
                    PriceObservations.selectAll().where { PriceObservations.itemId eq itemId }
                } else {
                    PriceObservations.selectAll()
                }
            query
                .orderBy(PriceObservations.id, SortOrder.DESC)
                .limit(minOf(limit ?: DEFAULT_LIMIT, MAX_LIMIT))
                .map { it.toObservationResponse() }
        }

    fun create(
        db: Database,
        req: CreateObservationRequest,
    ): ObservationResponse =
        transaction(db) {
            val id =
                PriceObservations.insert {
                    it[itemId] = req.itemId
                    it[server] = req.server
                    it[observedAt] = req.observedAt
                    it[priceSource] = req.source
                    it[confidenceScore] = req.confidenceScore
                    it[minPrice] = req.minPrice
                    it[avgPrice] = req.avgPrice
                    it[medianPrice] = req.medianPrice
                    it[lotSize] = req.lotSize
                    it[quantityAvailable] = req.quantityAvailable
                    it[rawPayload] = req.rawPayload
                    it[comment] = req.comment
                    it[elements] = req.elements
                    it[isDiscovered] = req.isDiscovered?.let { discovered -> if (discovered) 1 else 0 }
                    it[runeSlots] = req.runeSlots
                    it[captureUid] = req.captureUid
                }[PriceObservations.id]
            findById(id)!!
        }

    fun delete(
        db: Database,
        id: Int,
    ): Boolean =
        transaction(db) {
            // deleteWhere's lambda only brings the Table into implicit receiver scope (unlike
            // where/update, whose lambdas ARE the ISqlExpressionBuilder receiver directly) -- `eq`
            // is a member-extension of ISqlExpressionBuilder, so it must be reached via `it.run`.
            PriceObservations.deleteWhere { it.run { PriceObservations.id eq id } } > 0
        }

    // Appends the correction marker with no leading space, matching edit_prices.py's
    // `(row["Comment"] or "") + "[corrected_manually]"` exactly (verified against the real script).
    fun updatePrices(
        db: Database,
        id: Int,
        req: UpdatePricesRequest,
    ): ObservationResponse? =
        transaction(db) {
            val existing = findById(id) ?: return@transaction null
            PriceObservations.update({ PriceObservations.id eq id }) {
                it[minPrice] = req.minPrice
                it[avgPrice] = req.avgPrice
                it[medianPrice] = req.medianPrice
                it[comment] = (existing.comment ?: "") + "[corrected_manually]"
            }
            findById(id)
        }

    // Overwrites Comment entirely, matching edit_prices.py's `f"[{motif}]"` exactly -- this is
    // intentionally destructive (loses any prior comment text), not a bug to "fix" in v1.
    fun setFlag(
        db: Database,
        id: Int,
        motif: FlagMotif,
    ): ObservationResponse? =
        transaction(db) {
            val motifText =
                when (motif) {
                    FlagMotif.PARSING_ERROR -> "parsing_error"
                    FlagMotif.OUTLIER -> "outlier"
                    FlagMotif.DUPLICATE -> "duplicate"
                    FlagMotif.MANUAL_CHECK -> "manual_check"
                }
            val updated =
                PriceObservations.update({ PriceObservations.id eq id }) {
                    it[comment] = "[$motifText]"
                }
            if (updated == 0) null else findById(id)
        }

    /**
     * Distinct itemIds with at least one observation, most-recently-observed first -- the Prices
     * tab's default browse view when no name/level/rarity filter is set, so opening the Market
     * screen shows "what I've actually captured" (like walking into the HDV and seeing what's
     * listed) rather than an arbitrary slice of the full item catalog sorted by level.
     */
    fun recentlyObservedItemIds(
        db: Database,
        limit: Int,
    ): List<Int> =
        transaction(db) {
            PriceObservations
                .selectAll()
                .orderBy(PriceObservations.id, SortOrder.DESC)
                .asSequence()
                .map { it[PriceObservations.itemId] }
                .distinct()
                .take(limit)
                .toList()
        }

    fun latestForItem(
        db: Database,
        itemId: Int,
        server: String? = null,
    ): ObservationResponse? =
        transaction(db) {
            val query =
                if (server != null) {
                    PriceObservations.selectAll().where { (PriceObservations.itemId eq itemId) and (PriceObservations.server eq server) }
                } else {
                    PriceObservations.selectAll().where { PriceObservations.itemId eq itemId }
                }
            query
                .orderBy(PriceObservations.id, SortOrder.DESC)
                .limit(1)
                .map { it.toObservationResponse() }
                .firstOrNull()
        }

    /**
     * Batched [latestForItem]: one query for every id in [itemIds] instead of one query per id --
     * used by `/api/items/search`, which would otherwise run up to `limit` (default 50, max 200)
     * separate queries per search, on a path retriggered on every debounced keystroke.
     */
    fun latestForItems(
        db: Database,
        itemIds: Collection<Int>,
    ): Map<Int, ObservationResponse> =
        if (itemIds.isEmpty()) {
            emptyMap()
        } else {
            transaction(db) {
                PriceObservations
                    .selectAll()
                    .where { PriceObservations.itemId inList itemIds }
                    .orderBy(PriceObservations.id, SortOrder.DESC)
                    .asSequence()
                    .map { it.toObservationResponse() }
                    // Rows arrive most-recent-first, so the first occurrence per itemId is its latest.
                    .distinctBy { it.itemId }
                    .associateBy { it.itemId }
            }
        }

    private fun findById(id: Int): ObservationResponse? =
        PriceObservations
            .selectAll()
            .where { PriceObservations.id eq id }
            .map { it.toObservationResponse() }
            .firstOrNull()

    private fun ResultRow.toObservationResponse() =
        ObservationResponse(
            id = this[PriceObservations.id],
            itemId = this[PriceObservations.itemId],
            server = this[PriceObservations.server],
            observedAt = this[PriceObservations.observedAt],
            source = this[PriceObservations.priceSource],
            confidenceScore = this[PriceObservations.confidenceScore],
            minPrice = this[PriceObservations.minPrice],
            avgPrice = this[PriceObservations.avgPrice],
            medianPrice = this[PriceObservations.medianPrice],
            lotSize = this[PriceObservations.lotSize],
            quantityAvailable = this[PriceObservations.quantityAvailable],
            rawPayload = this[PriceObservations.rawPayload],
            comment = this[PriceObservations.comment],
            elements = this[PriceObservations.elements],
            isDiscovered = this[PriceObservations.isDiscovered]?.let { it != 0 },
            runeSlots = this[PriceObservations.runeSlots],
            captureUid = this[PriceObservations.captureUid]
        )
}
