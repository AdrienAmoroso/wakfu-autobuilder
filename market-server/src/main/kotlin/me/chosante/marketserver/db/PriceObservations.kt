package me.chosante.marketserver.db

import org.jetbrains.exposed.v1.core.Table

// Column types below match the PHYSICAL schema of the shared wakfu.db file (verified with
// `sqlite3 ... typeof(col)` against real rows written by the existing Python capture pipeline),
// not the types the original C#/EF Core migration declared. In particular CaptureUid and
// Elements are declared INTEGER in the DDL but every real row holds a TEXT value there --
// SQLite's manifest typing allows this silently. Mapping them as String here avoids a runtime
// type-conversion failure on read.
object PriceObservations : Table("PriceObservations") {
    val id = integer("Id").autoIncrement()
    val itemId = integer("ItemId")
    val server = text("Server")
    val observedAt = text("ObservedAt")

    // Named `priceSource`, not `source` -- `source` collides with ColumnSet.source (Exposed's
    // own join-tracking member) and fails to compile without an `override` modifier.
    val priceSource = text("Source")
    val confidenceScore = double("ConfidenceScore")
    val minPrice = long("MinPrice")
    val avgPrice = long("AvgPrice")
    val medianPrice = long("MedianPrice").nullable()
    val lotSize = integer("LotSize").nullable()
    val quantityAvailable = integer("QuantityAvailable").nullable()
    val rawPayload = text("RawPayload").nullable()
    val comment = text("Comment").nullable()
    val elements = text("Elements").nullable()
    val isDiscovered = integer("IsDiscovered").nullable()
    val runeSlots = text("RuneSlots").nullable()
    val captureUid = text("CaptureUid").nullable()

    override val primaryKey = PrimaryKey(id)
}
