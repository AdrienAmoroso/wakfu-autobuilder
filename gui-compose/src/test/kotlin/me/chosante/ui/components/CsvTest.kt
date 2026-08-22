package me.chosante.ui.components

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CsvTest {
    @Test
    fun `plain fields are joined with a comma, no quoting`() {
        val csv = toCsv(listOf("a", "b"), listOf(listOf("1", "2")))

        assertThat(csv).isEqualTo("﻿a,b\r\n1,2\r\n")
    }

    @Test
    fun `a field containing a comma is quoted`() {
        val csv = toCsv(listOf("name"), listOf(listOf("Gobball Skin, Rare")))

        assertThat(csv).isEqualTo("﻿name\r\n\"Gobball Skin, Rare\"\r\n")
    }

    @Test
    fun `a field containing a quote is quoted and its internal quotes are doubled`() {
        val csv = toCsv(listOf("name"), listOf(listOf("The \"Big\" Sword")))

        assertThat(csv).isEqualTo("﻿name\r\n\"The \"\"Big\"\" Sword\"\r\n")
    }

    @Test
    fun `a field containing a newline is quoted`() {
        val csv = toCsv(listOf("name"), listOf(listOf("Line1\nLine2")))

        assertThat(csv).isEqualTo("﻿name\r\n\"Line1\nLine2\"\r\n")
    }

    @Test
    fun `multiple rows each end with a CRLF`() {
        val csv = toCsv(listOf("id"), listOf(listOf("1"), listOf("2")))

        assertThat(csv).isEqualTo("﻿id\r\n1\r\n2\r\n")
    }
}
