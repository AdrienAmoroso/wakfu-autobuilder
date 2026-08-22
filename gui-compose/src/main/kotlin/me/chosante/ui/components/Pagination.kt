package me.chosante.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import me.chosante.ui.i18n.Tr
import me.chosante.ui.i18n.tr
import me.chosante.ui.theme.WColor
import me.chosante.ui.theme.WTypography

/** Rows per page for any client-side-paginated list (Market's Prices tab, Kamas's three tabs) --
 * each already fetches its full filtered/scanned set in one call (see
 * `BuildSearchModel.FULL_CATALOG_LIMIT`), so pagination here is purely a display chunk size. */
const val LIST_PAGE_SIZE = 50

/** Page count for a client-side-paginated list of [totalResults] items, always >= 1 (so an empty
 * result set still reads as "Page 1 / 1" rather than a div-by-zero "Page 1 / 0"). */
fun pageCount(totalResults: Int): Int = if (totalResults == 0) 1 else (totalResults + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE

/** Previous/Next + "Page X / Y", client-side over an already-fetched full result set. */
@Composable
internal fun PageControls(
    page: Int,
    pageCount: Int,
    onSetPage: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SmallButton(text = tr(Tr.PREVIOUS_PAGE), onClick = { onSetPage(page - 1) }, enabled = page > 0)
        Text(text = tr(Tr.PAGE_LABEL).format(page + 1, pageCount), style = WTypography.labelMedium.copy(color = WColor.muted))
        SmallButton(text = tr(Tr.NEXT_PAGE), onClick = { onSetPage(page + 1) }, enabled = page < pageCount - 1)
    }
}
