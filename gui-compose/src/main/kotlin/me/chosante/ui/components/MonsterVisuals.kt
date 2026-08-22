package me.chosante.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.chosante.autobuilder.domain.SpellElement
import me.chosante.autobuilder.domain.resistancePercent
import me.chosante.common.Monster
import me.chosante.ui.theme.WColor
import me.chosante.ui.theme.WType
import me.chosante.ui.theme.WTypography

/** Accent colour for an element's resistance badge, matching the elemental-mastery palette. */
internal fun SpellElement.accent(): Color =
    when (this) {
        SpellElement.FIRE -> WColor.fire
        SpellElement.WATER -> WColor.water
        SpellElement.EARTH -> WColor.earth
        SpellElement.AIR -> WColor.air
    }

/** Two-letter element code, matching the stat-catalog glyphs (Fi/Wa/Ea/Ai). */
internal fun SpellElement.shortCode(): String =
    when (this) {
        SpellElement.FIRE -> "Fi"
        SpellElement.WATER -> "Wa"
        SpellElement.EARTH -> "Ea"
        SpellElement.AIR -> "Ai"
    }

/**
 * The boss's four elemental resistances as compact coloured badges (lower % = a weaker, better-to-hit
 * element). [highlight] emphasises a specific element (a forced choice); otherwise the weakest — the
 * one the objective would most likely auto-pick — is emphasised, UNLESS all four resistances tie
 * (most commonly all-zero — true for 877/2846 monsters once this component started covering every
 * monster, not just the ~226 bosses it was built for, where a tie is rare): `minByOrNull` would
 * otherwise silently pick the first element (Fire, by [SpellElement.entries] order) and imply a fake
 * weakness that isn't real. No chip is emphasised in that case, which the existing chip styling
 * already renders as a neutral "no notable weakness" state.
 */
@Composable
internal fun BossResistanceChips(
    boss: Monster,
    highlight: SpellElement? = null,
    modifier: Modifier = Modifier,
) {
    val resistances = SpellElement.entries.map { it to boss.resistancePercent(it) }
    val hasNotableWeakness = resistances.map { it.second }.distinct().size > 1
    val emphasised = highlight ?: if (hasNotableWeakness) resistances.minByOrNull { it.second }?.first else null
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        resistances.forEach { (element, percent) ->
            val on = element == emphasised
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(element.accent().copy(alpha = if (on) 0.22f else 0.1f))
                        .border(1.dp, element.accent().copy(alpha = if (on) 0.7f else 0.25f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = element.shortCode(),
                    style = WTypography.labelSmall.copy(color = element.accent(), fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "$percent%",
                    style = WTypography.labelSmall.copy(color = if (on) WColor.text else WColor.muted, fontFamily = WType.mono)
                )
            }
        }
    }
}

/**
 * Resolves a monster's icon under `assets/monsters/` — the committed 200×200 boss portraits keyed by
 * [Monster.gfx]. (Unlike item/spell icons, monster portraits are NOT extracted from the client's gui.jar —
 * it only keys monsters by gfx as 132×41 banners — so the boss-picker portraits stay committed-static.
 * Most non-boss monsters, and a handful of bosses, have no portrait — see [MonsterIcon].) Returns `null`
 * when the sprite id is absent or the asset is missing, so callers degrade to a placeholder rather
 * than crash. Mirrors [BreedAssets].
 */
internal object MonsterAssets {
    fun iconPath(monster: Monster): String? = monster.gfx?.let { existing("assets/monsters/$it.png") }

    private fun existing(path: String): String? = if (Thread.currentThread().contextClassLoader.getResource(path) != null) path else null
}

/**
 * A monster's icon in a rounded tile (mirrors [ItemThumbnail]). Most monsters have no portrait asset
 * (only bosses and a few others were ever crawled) — that's an expected, common state, not a broken
 * one, so it shows a muted placeholder glyph rather than a blank tile that reads as missing/loading art.
 */
@Composable
internal fun MonsterIcon(
    monster: Monster,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(WColor.bg),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = MonsterAssets.iconPath(monster)?.let { rememberClasspathBitmap(it) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = monster.name.en,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size).padding(3.dp)
            )
        } else {
            Text(text = "?", style = WTypography.labelMedium.copy(color = WColor.faint, fontWeight = FontWeight.SemiBold))
        }
    }
}
