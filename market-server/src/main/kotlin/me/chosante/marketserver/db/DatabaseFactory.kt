package me.chosante.marketserver.db

import me.chosante.marketserver.Config
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

// The literal DDL below is copied verbatim from the live, EF-Core-created wakfu.db file
// (verified with `sqlite3 wakfu.db ".schema PriceObservations"`), NOT generated via
// SchemaUtils.create -- Exposed's DSL-generated DDL would not reproduce the exact named
// constraints / FK / partial unique index. CREATE ... IF NOT EXISTS makes this a no-op against
// the user's existing file while making market-server self-sufficient on a machine where the
// old .NET app (the previous schema owner) never ran.
private const val CREATE_TABLE_SQL = """
CREATE TABLE IF NOT EXISTS "PriceObservations" (
    "Id" INTEGER NOT NULL CONSTRAINT "PK_PriceObservations" PRIMARY KEY AUTOINCREMENT,
    "ItemId" INTEGER NOT NULL,
    "Server" TEXT NOT NULL,
    "ObservedAt" TEXT NOT NULL,
    "Source" TEXT NOT NULL,
    "ConfidenceScore" REAL NOT NULL,
    "MinPrice" INTEGER NOT NULL,
    "AvgPrice" INTEGER NOT NULL,
    "MedianPrice" INTEGER NULL,
    "LotSize" INTEGER NULL,
    "QuantityAvailable" INTEGER NULL,
    "RawPayload" TEXT NULL,
    "Comment" TEXT NULL, "Elements" INTEGER NULL, "IsDiscovered" INTEGER NULL, "RuneSlots" TEXT NULL, "CaptureUid" INTEGER NULL,
    CONSTRAINT "FK_PriceObservations_Items_ItemId" FOREIGN KEY ("ItemId") REFERENCES "Items" ("Id") ON DELETE CASCADE
)
"""

private const val CREATE_INDEX_SQL =
    """CREATE INDEX IF NOT EXISTS "IX_PriceObservations_ItemId_Server_ObservedAt" ON "PriceObservations" ("ItemId", "Server", "ObservedAt")"""

private const val CREATE_UNIQUE_INDEX_SQL =
    """CREATE UNIQUE INDEX IF NOT EXISTS "IX_PriceObservations_CaptureUid" ON "PriceObservations" ("CaptureUid") WHERE CaptureUid IS NOT NULL"""

object DatabaseFactory {
    // Returns the connected Database explicitly rather than relying on Exposed's implicit
    // JVM-global "current default database" -- with several Application instances (one per test)
    // each calling connect() against their own SQLite file within the same JVM, that global
    // registry can flap between them. Callers must pass this instance into every
    // transaction(db) { ... } call instead of using the no-arg transaction { ... } form.
    fun init(dbPath: String = Config.dbPath): Database {
        // No `PRAGMA foreign_keys = ON` anywhere: the existing Python importer already relies on
        // FK enforcement being off (it inserts PriceObservations rows without checking that a
        // matching Items row exists), and SQLite defaults to off unless a client opts in.
        val database = Database.connect("jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")
        transaction(database) {
            exec(CREATE_TABLE_SQL)
            exec(CREATE_INDEX_SQL)
            exec(CREATE_UNIQUE_INDEX_SQL)
        }
        return database
    }
}
