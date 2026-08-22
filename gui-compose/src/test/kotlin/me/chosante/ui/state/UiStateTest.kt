package me.chosante.ui.state

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UiStateTest {
    @Test
    fun `an empty result set is still one page, not zero`() {
        assertThat(marketPageCount(0)).isEqualTo(1)
    }

    @Test
    fun `a result count that divides evenly is not given a spurious extra empty page`() {
        assertThat(marketPageCount(MARKET_PAGE_SIZE)).isEqualTo(1)
        assertThat(marketPageCount(MARKET_PAGE_SIZE * 2)).isEqualTo(2)
    }

    @Test
    fun `a partial last page is still counted`() {
        assertThat(marketPageCount(MARKET_PAGE_SIZE + 1)).isEqualTo(2)
        assertThat(marketPageCount(1)).isEqualTo(1)
    }

    @Test
    fun `matches the real catalog scale (equipment alone is over 7700 items)`() {
        assertThat(marketPageCount(7730)).isEqualTo((7730 + MARKET_PAGE_SIZE - 1) / MARKET_PAGE_SIZE)
    }
}
