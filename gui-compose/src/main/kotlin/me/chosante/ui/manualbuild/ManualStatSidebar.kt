package me.chosante.ui.manualbuild

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.chosante.common.Characteristic
import me.chosante.ui.components.CharacteristicIcon
import me.chosante.ui.components.Hairline
import me.chosante.ui.i18n.Lang
import me.chosante.ui.i18n.LocalLang
import me.chosante.ui.i18n.Tr
import me.chosante.ui.i18n.label
import me.chosante.ui.i18n.tr
import me.chosante.ui.state.SPECIALIZED_MASTERIES
import me.chosante.ui.state.UiState
import me.chosante.ui.state.formatCompact
import me.chosante.ui.state.isEngineInternalStat
import me.chosante.ui.stats.ResultCard
import me.chosante.ui.theme.WColor
import me.chosante.ui.theme.WDimens
import me.chosante.ui.theme.WType
import me.chosante.ui.theme.WTypography

private val VITALS = listOf(Characteristic.HP, Characteristic.ACTION_POINT, Characteristic.MOVEMENT_POINT, Characteristic.WAKFU_POINT)

private val ELEMENTS =
    listOf(
        Characteristic.MASTERY_ELEMENTARY_WATER to Characteristic.RESISTANCE_ELEMENTARY_WATER,
        Characteristic.MASTERY_ELEMENTARY_FIRE to Characteristic.RESISTANCE_ELEMENTARY_FIRE,
        Characteristic.MASTERY_ELEMENTARY_EARTH to Characteristic.RESISTANCE_ELEMENTARY_EARTH,
        Characteristic.MASTERY_ELEMENTARY_WIND to Characteristic.RESISTANCE_ELEMENTARY_WIND
    )

// "Soin réalisé" (MASTERY_HEALING) sits in Combat here, per the user's own Wakfuli-derived grouping --
// elsewhere in this codebase (MasterySummary) it's classed as a specialized mastery instead.
private val COMBAT =
    listOf(
        Characteristic.DAMAGE_INFLICTED,
        Characteristic.CRITICAL_HIT,
        Characteristic.INITIATIVE,
        Characteristic.DODGE,
        Characteristic.WISDOM,
        Characteristic.WILLPOWER,
        Characteristic.MASTERY_HEALING,
        Characteristic.BLOCK_PERCENTAGE,
        Characteristic.RANGE,
        Characteristic.LOCK,
        Characteristic.PROSPECTION
    )

private val SHOWN_ELSEWHERE: Set<Characteristic> =
    (VITALS + ELEMENTS.flatMap { (m, r) -> listOf(m, r) } + COMBAT).toSet()

/**
 * Wakfu's own "total mastery" convention (matching the user's correction): every specialized mastery
 * is summed, but the 4 elemental masteries are NOT -- only the single highest one counts, since a hit
 * always uses your best element. [SPECIALIZED_MASTERIES] (shared with `engineMasteryScore`) is exactly
 * {distance, critical, back, melee, berserk, healing} -- deliberately NOT reusing `engineMasteryScore`
 * itself, which takes the *minimum* requested element (the solver's worst-case objective, a different
 * question from "what's your best hit"). Control is a distinct mechanic, never a mastery, and is
 * correctly absent from both sets. `internal` (not `private`) so it's unit-testable without Compose.
 */
internal fun manualTotalMastery(achieved: Map<Characteristic, Int>): Int =
    SPECIALIZED_MASTERIES.sumOf { achieved[it] ?: 0 } + (ELEMENTS.maxOfOrNull { (mastery, _) -> achieved[mastery] ?: 0 } ?: 0)

/**
 * The manual-construction screen's left sidebar: one scrollable column, 4 grouped blocks, mirroring
 * the layout the user described from wakfuli.com's builder -- Vitals / Mastery & Resistance (with
 * totals) / Combat / Secondary (everything else). Reuses [ResultCard] (promoted `internal` in
 * `StatsPanel.kt`) so the visual idiom matches the rest of the app exactly; only the grouping is new.
 */
@Composable
fun ManualStatSidebar(
    ui: UiState,
    modifier: Modifier = Modifier,
) {
    val achieved = ui.manualAchieved
    val lang = LocalLang.current
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WDimens.gap),
        verticalArrangement = Arrangement.spacedBy(WDimens.gap)
    ) {
        ResultCard(title = tr(Tr.MANUAL_SIDEBAR_VITALS)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                VITALS.forEach { characteristic -> VitalTile(characteristic = characteristic, value = achieved[characteristic] ?: 0, lang = lang) }
            }
        }

        val totalMastery = manualTotalMastery(achieved)
        val totalResistance = ELEMENTS.sumOf { (_, resistance) -> achieved[resistance] ?: 0 }
        ResultCard(title = tr(Tr.MANUAL_SIDEBAR_MASTERY_RESISTANCE)) {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                TotalMetric(label = tr(Tr.MANUAL_TOTAL_MASTERY), value = totalMastery, modifier = Modifier.weight(1f))
                TotalMetric(label = tr(Tr.MANUAL_TOTAL_RESISTANCE), value = totalResistance, modifier = Modifier.weight(1f))
            }
            ELEMENTS.forEachIndexed { index, (mastery, resistance) ->
                if (index > 0) Hairline()
                ElementRow(mastery = mastery, resistance = resistance, achieved = achieved, lang = lang)
            }
        }

        ResultCard(title = tr(Tr.MANUAL_SIDEBAR_COMBAT)) {
            COMBAT.forEachIndexed { index, characteristic ->
                if (index > 0) Hairline()
                StatRow(characteristic = characteristic, value = achieved[characteristic] ?: 0, lang = lang)
            }
        }

        val secondary =
            achieved
                .filterValues { it != 0 }
                .filterKeys { it !in SHOWN_ELSEWHERE && !it.isEngineInternalStat() }
                .entries
                .sortedBy { it.key.ordinal }
        if (secondary.isNotEmpty()) {
            ResultCard(title = tr(Tr.MANUAL_SIDEBAR_SECONDARY)) {
                secondary.forEachIndexed { index, (characteristic, value) ->
                    if (index > 0) Hairline()
                    StatRow(characteristic = characteristic, value = value, lang = lang)
                }
            }
        }
    }
}

@Composable
private fun VitalTile(
    characteristic: Characteristic,
    value: Int,
    lang: Lang,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CharacteristicIcon(characteristic = characteristic, size = 18.dp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value.formatCompact(), style = WTypography.bodyMedium.copy(fontFamily = WType.mono, color = WColor.text, fontWeight = FontWeight.SemiBold))
        Text(text = characteristic.label(lang), style = WTypography.labelSmall.copy(color = WColor.muted), maxLines = 1)
    }
}

@Composable
private fun TotalMetric(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = label, style = WTypography.labelSmall.copy(color = WColor.muted))
        Text(text = value.formatCompact(), style = WTypography.headlineMedium.copy(fontFamily = WType.mono, color = WColor.text))
    }
}

@Composable
private fun ElementRow(
    mastery: Characteristic,
    resistance: Characteristic,
    achieved: Map<Characteristic, Int>,
    lang: Lang,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        CharacteristicIcon(characteristic = mastery, size = 16.dp)
        Spacer(modifier = Modifier.width(9.dp))
        Text(text = mastery.label(lang), style = WTypography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
        Text(
            text = (achieved[mastery] ?: 0).formatCompact(),
            style = WTypography.bodyMedium.copy(fontFamily = WType.mono, color = WColor.text),
            modifier = Modifier.width(56.dp)
        )
        CharacteristicIcon(characteristic = resistance, size = 16.dp)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = (achieved[resistance] ?: 0).formatCompact(),
            style = WTypography.bodyMedium.copy(fontFamily = WType.mono, color = WColor.muted),
            modifier = Modifier.width(48.dp)
        )
    }
}

@Composable
private fun StatRow(
    characteristic: Characteristic,
    value: Int,
    lang: Lang,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        CharacteristicIcon(characteristic = characteristic, size = 16.dp)
        Spacer(modifier = Modifier.width(9.dp))
        Text(
            text = characteristic.label(lang),
            style = WTypography.bodyMedium.copy(color = if (value < 0) WColor.danger else WColor.text),
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (value > 0) "+${value.formatCompact()}" else value.formatCompact(),
            style = WTypography.bodyMedium.copy(fontFamily = WType.mono, fontWeight = FontWeight.SemiBold, color = if (value < 0) WColor.danger else WColor.muted)
        )
    }
}
