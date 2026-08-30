package me.chosante.ui.manualbuild

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.chosante.ui.i18n.Tr
import me.chosante.ui.i18n.tr
import me.chosante.ui.state.ManualTab
import me.chosante.ui.theme.WColor
import me.chosante.ui.theme.WTypography

/**
 * The 5-tab strip atop [ManualBuildScreen]'s center panel -- same pill idiom as the auto-Builder's
 * `ResultTabHeader` (`AppShell.kt`), recreated locally since that one is `private` and this screen's
 * tab set is unrelated (5 tabs, not 2).
 */
@Composable
fun ManualBuildTabStrip(
    current: ManualTab,
    onSelect: (ManualTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(WColor.bg)
                .border(1.dp, WColor.border, RoundedCornerShape(8.dp))
                .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        ManualTabButton(label = tr(Tr.MANUAL_TAB_ITEMS), selected = current == ManualTab.ITEMS) { onSelect(ManualTab.ITEMS) }
        ManualTabButton(label = tr(Tr.MANUAL_TAB_ENCHANTMENT), selected = current == ManualTab.ENCHANTMENT) { onSelect(ManualTab.ENCHANTMENT) }
        ManualTabButton(label = tr(Tr.MANUAL_TAB_APTITUDE), selected = current == ManualTab.APTITUDE) { onSelect(ManualTab.APTITUDE) }
        ManualTabButton(label = tr(Tr.MANUAL_TAB_SPELLS), selected = current == ManualTab.SPELLS) { onSelect(ManualTab.SPELLS) }
        ManualTabButton(label = tr(Tr.MANUAL_TAB_NOTE), selected = current == ManualTab.NOTE) { onSelect(ManualTab.NOTE) }
    }
}

@Composable
private fun ManualTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) WColor.raised else Color.Transparent)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style =
                WTypography.labelMedium.copy(
                    color = if (selected) WColor.text else WColor.muted,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
        )
    }
}
