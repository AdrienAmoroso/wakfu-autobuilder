package me.chosante.ui.components

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PaginationTest {
    @Test
    fun `an empty result set is still one page, not zero`() {
        assertThat(pageCount(0)).isEqualTo(1)
    }

    @Test
    fun `a result count that divides evenly is not given a spurious extra empty page`() {
        assertThat(pageCount(LIST_PAGE_SIZE)).isEqualTo(1)
        assertThat(pageCount(LIST_PAGE_SIZE * 2)).isEqualTo(2)
    }

    @Test
    fun `a partial last page is still counted`() {
        assertThat(pageCount(LIST_PAGE_SIZE + 1)).isEqualTo(2)
        assertThat(pageCount(1)).isEqualTo(1)
    }

    @Test
    fun `matches the real catalog scale (equipment alone is over 7700 items)`() {
        assertThat(pageCount(7730)).isEqualTo((7730 + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE)
    }

    @Test
    fun `matches the Monster Farming catalog scale (2846 monsters, not just ones with drops)`() {
        assertThat(pageCount(2846)).isEqualTo((2846 + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE)
    }
}
