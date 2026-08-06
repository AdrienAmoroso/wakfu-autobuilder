package me.chosante.marketserver.db

import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Test
import java.nio.file.Files

class DatabaseFactoryTest {
    @Test
    fun `init is idempotent and creates a queryable empty table`() {
        val dbFile = Files.createTempFile("market-test", ".db")
        Files.deleteIfExists(dbFile)

        DatabaseFactory.init(dbFile.toString())
        DatabaseFactory.init(dbFile.toString())

        val rows = transaction { PriceObservations.selectAll().count() }

        assertThat(rows).isZero()

        Files.deleteIfExists(dbFile)
    }
}
