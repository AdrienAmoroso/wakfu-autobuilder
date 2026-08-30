package me.chosante.ui.manualbuild

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.chosante.common.skills.AgilityCharacteristic
import me.chosante.common.skills.CharacterSkills
import me.chosante.common.skills.IntelligenceCharacteristic
import me.chosante.common.skills.LuckCharacteristic
import me.chosante.common.skills.MajorCharacteristic
import me.chosante.common.skills.SkillCharacteristic
import me.chosante.common.skills.StrengthCharacteristic
import me.chosante.ui.components.Hairline
import me.chosante.ui.components.iconResourcePath
import me.chosante.ui.components.rememberClasspathBitmap
import me.chosante.ui.i18n.LocalLang
import me.chosante.ui.i18n.Tr
import me.chosante.ui.i18n.skillLabel
import me.chosante.ui.i18n.tr
import me.chosante.ui.stats.ResultCard
import me.chosante.ui.theme.WColor
import me.chosante.ui.theme.WType
import me.chosante.ui.theme.WTypography
import kotlin.math.min

/**
 * One editable skill-point row. [onChange] returns the WHOLE [CharacterSkills] with just this row's
 * value replaced -- every branch (`Strength`/`Agility`/.../`Major`) is a `data class` with immutable
 * `val` fields holding freshly-constructed [SkillCharacteristic] instances (each takes a plain
 * `pointsAssigned: Int` constructor), so `.copy(field = FreshInstance(newValue))` is the safe,
 * idiomatic way to change one value -- this file never calls the low-level, in-place-mutating
 * `SkillCharacteristic.setPointAssigned` on an instance already referenced by
 * [me.chosante.ui.state.UiState.manualBuild], which would corrupt already-rendered Compose state.
 */
internal data class ManualSkillRow(
    val name: String,
    val points: Int,
    val max: Int,
    val iconPath: String?,
    val onChange: (Int) -> CharacterSkills,
)

internal data class ManualSkillBranchUi(
    val labelKey: Tr,
    val color: Color,
    val points: Int,
    val max: Int,
    val rows: List<ManualSkillRow>,
)

internal fun manualSkillBranches(skills: CharacterSkills): List<ManualSkillBranchUi> =
    listOf(
        intelligenceBranch(skills),
        strengthBranch(skills),
        agilityBranch(skills),
        luckBranch(skills),
        majorBranch(skills)
    )

/** How many points [characteristic] could hold if every other point currently allocated in the
 * branch were freed up -- matches the read-only `SkillTree`'s own "x/max" display, now used as the
 * actual ceiling for the +/- controls. */
private fun rowMax(
    characteristic: SkillCharacteristic,
    branchMaxPointsToAssign: Int,
    branchPointsAssigned: Int,
): Int = min(characteristic.maxPointsAssignable, branchMaxPointsToAssign - branchPointsAssigned + characteristic.pointsAssigned)

private fun strengthBranch(skills: CharacterSkills): ManualSkillBranchUi {
    val s = skills.strength
    val assigned = s.pointsAssigned()

    fun row(
        characteristic: SkillCharacteristic,
        update: (Int) -> me.chosante.common.skills.Strength,
    ) = ManualSkillRow(
        name = characteristic.name,
        points = characteristic.pointsAssigned,
        max = rowMax(characteristic, s.maxPointsToAssign, assigned),
        iconPath = characteristic.iconResourcePath(),
        onChange = { v -> skills.copy(strength = update(v)) }
    )
    return ManualSkillBranchUi(
        labelKey = Tr.BRANCH_STRENGTH,
        color = WColor.fire,
        points = assigned,
        max = s.maxPointsToAssign,
        rows =
            listOf(
                row(s.masteryElementary) { s.copy(masteryElementary = StrengthCharacteristic.MasteryElementary(it)) },
                row(s.masteryMelee) { s.copy(masteryMelee = StrengthCharacteristic.MasteryMelee(it)) },
                row(s.masteryDistance) { s.copy(masteryDistance = StrengthCharacteristic.MasteryDistance(it)) },
                row(s.hp) { s.copy(hp = StrengthCharacteristic.Hp(it)) }
            )
    )
}

private fun agilityBranch(skills: CharacterSkills): ManualSkillBranchUi {
    val a = skills.agility
    val assigned = a.pointsAssigned()

    fun row(
        characteristic: SkillCharacteristic,
        update: (Int) -> me.chosante.common.skills.Agility,
    ) = ManualSkillRow(
        name = characteristic.name,
        points = characteristic.pointsAssigned,
        max = rowMax(characteristic, a.maxPointsToAssign, assigned),
        iconPath = characteristic.iconResourcePath(),
        onChange = { v -> skills.copy(agility = update(v)) }
    )
    return ManualSkillBranchUi(
        labelKey = Tr.BRANCH_AGILITY,
        color = WColor.accent2,
        points = assigned,
        max = a.maxPointsToAssign,
        rows =
            listOf(
                row(a.lock) { a.copy(lock = AgilityCharacteristic.Lock(it)) },
                row(a.dodge) { a.copy(dodge = AgilityCharacteristic.Dodge(it)) },
                row(a.initiative) { a.copy(initiative = AgilityCharacteristic.Initiative(it)) },
                row(a.dodgeAndLock) { a.copy(dodgeAndLock = AgilityCharacteristic.DodgeAndLock(it)) },
                row(a.willpower) { a.copy(willpower = AgilityCharacteristic.Willpower(it)) }
            )
    )
}

private fun luckBranch(skills: CharacterSkills): ManualSkillBranchUi {
    val l = skills.luck
    val assigned = l.pointsAssigned()

    fun row(
        characteristic: SkillCharacteristic,
        update: (Int) -> me.chosante.common.skills.Luck,
    ) = ManualSkillRow(
        name = characteristic.name,
        points = characteristic.pointsAssigned,
        max = rowMax(characteristic, l.maxPointsToAssign, assigned),
        iconPath = characteristic.iconResourcePath(),
        onChange = { v -> skills.copy(luck = update(v)) }
    )
    return ManualSkillBranchUi(
        labelKey = Tr.BRANCH_LUCK,
        color = Color(0xFFE8C24A),
        points = assigned,
        max = l.maxPointsToAssign,
        rows =
            listOf(
                row(l.criticalHit) { l.copy(criticalHit = LuckCharacteristic.CriticalHit(it)) },
                row(l.block) { l.copy(block = LuckCharacteristic.Block(it)) },
                row(l.masteryCritical) { l.copy(masteryCritical = LuckCharacteristic.MasteryCritical(it)) },
                row(l.masteryBack) { l.copy(masteryBack = LuckCharacteristic.MasteryBack(it)) },
                row(l.masteryBerserk) { l.copy(masteryBerserk = LuckCharacteristic.MasteryBerserk(it)) },
                row(l.masteryHealing) { l.copy(masteryHealing = LuckCharacteristic.MasteryHealing(it)) },
                row(l.resistanceBack) { l.copy(resistanceBack = LuckCharacteristic.ResistanceBack(it)) },
                row(l.resistanceCritical) { l.copy(resistanceCritical = LuckCharacteristic.ResistanceCritical(it)) }
            )
    )
}

private fun intelligenceBranch(skills: CharacterSkills): ManualSkillBranchUi {
    val i = skills.intelligence
    val assigned = i.pointsAssigned()

    fun row(
        characteristic: SkillCharacteristic,
        update: (Int) -> me.chosante.common.skills.Intelligence,
    ) = ManualSkillRow(
        name = characteristic.name,
        points = characteristic.pointsAssigned,
        max = rowMax(characteristic, i.maxPointsToAssign, assigned),
        iconPath = characteristic.iconResourcePath(),
        onChange = { v -> skills.copy(intelligence = update(v)) }
    )
    return ManualSkillBranchUi(
        labelKey = Tr.BRANCH_INTELLIGENCE,
        color = WColor.earth,
        points = assigned,
        max = i.maxPointsToAssign,
        rows =
            listOf(
                row(i.hpPercentage) { i.copy(hpPercentage = IntelligenceCharacteristic.HpPercentage(it)) },
                row(i.resistance) { i.copy(resistance = IntelligenceCharacteristic.Resistance(it)) },
                row(i.shield) { i.copy(shield = IntelligenceCharacteristic.Shield(it)) },
                row(i.healReceivedPercentage) { i.copy(healReceivedPercentage = IntelligenceCharacteristic.HealReceivedPercentage(it)) },
                row(i.hpPercentageAsArmor) { i.copy(hpPercentageAsArmor = IntelligenceCharacteristic.HpPercentageAsArmor(it)) }
            )
    )
}

private fun majorBranch(skills: CharacterSkills): ManualSkillBranchUi {
    val m = skills.major
    val assigned = m.pointsAssigned()

    fun row(
        characteristic: SkillCharacteristic,
        update: (Int) -> me.chosante.common.skills.Major,
    ) = ManualSkillRow(
        name = characteristic.name,
        points = characteristic.pointsAssigned,
        max = rowMax(characteristic, m.maxPointsToAssign, assigned),
        iconPath = characteristic.iconResourcePath(),
        onChange = { v -> skills.copy(major = update(v)) }
    )
    return ManualSkillBranchUi(
        labelKey = Tr.BRANCH_MAJOR,
        color = WColor.accent,
        points = assigned,
        max = m.maxPointsToAssign,
        rows =
            listOf(
                row(m.actionPoint) { m.copy(actionPoint = MajorCharacteristic.ActionPoint(it)) },
                row(m.movementPointAndMasteryElementary) {
                    m.copy(movementPointAndMasteryElementary = MajorCharacteristic.MovementPointWithMasteryElementary(it))
                },
                row(m.rangeAndMasteryElementary) { m.copy(rangeAndMasteryElementary = MajorCharacteristic.RangeWithMasteryElementary(it)) },
                row(m.wakfuPoints) { m.copy(wakfuPoints = MajorCharacteristic.WakfuPoints(it)) },
                row(m.controlAndMasteryElementary) {
                    m.copy(controlAndMasteryElementary = MajorCharacteristic.ControlWithMasteryElementary(it))
                },
                row(m.damageInflicted) { m.copy(damageInflicted = MajorCharacteristic.DamageInflicted(it)) },
                row(m.resistance) { m.copy(resistance = MajorCharacteristic.Resistance(it)) }
            )
    )
}

/**
 * The manual-construction screen's INTERACTIVE counterpart to [me.chosante.ui.stats.StatsPanel]'s
 * read-only skill tree -- same [ResultCard] styling, but each line gets +/- controls wired through
 * [manualSkillBranches]. [onChange] receives the whole new [CharacterSkills] (see that function's
 * doc for why the immutable `.copy()` chain is safe here).
 */
@Composable
internal fun ManualSkillTree(
    skills: CharacterSkills,
    onChange: (CharacterSkills) -> Unit,
) {
    val branches = manualSkillBranches(skills)
    val lang = LocalLang.current
    ResultCard(
        title = tr(Tr.MANUAL_SKILLS_TITLE),
        trailing = "${branches.sumOf { it.points }}/${branches.sumOf { it.max }}"
    ) {
        branches.forEachIndexed { index, branch ->
            if (index > 0) Hairline()
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(branch.color)
                    )
                    Spacer(modifier = Modifier.width(9.dp))
                    Text(
                        text = tr(branch.labelKey),
                        style = WTypography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${branch.points}/${branch.max}",
                        style = WTypography.labelSmall.copy(fontFamily = WType.mono, color = WColor.muted)
                    )
                }
                Column(
                    modifier = Modifier.padding(start = 17.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    branch.rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                val bitmap = row.iconPath?.let { rememberClasspathBitmap(it) }
                                if (bitmap != null) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .size(20.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(WColor.iconTile),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = skillLabel(row.name, lang),
                                    style =
                                        WTypography.bodySmall.copy(
                                            color = if (row.points > 0) WColor.text else WColor.muted,
                                            lineHeight = 15.sp
                                        ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            SkillStepButton(glyph = "−", enabled = row.points > 0, onClick = { onChange(row.onChange(row.points - 1)) })
                            Text(
                                text = "${row.points}/${row.max}",
                                style = WTypography.bodySmall.copy(fontFamily = WType.mono, color = WColor.muted),
                                modifier = Modifier.width(44.dp)
                            )
                            SkillStepButton(glyph = "+", enabled = row.points < row.max, onClick = { onChange(row.onChange(row.points + 1)) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillStepButton(
    glyph: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (enabled) WColor.surface else Color.Transparent)
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = glyph,
            style = WTypography.labelSmall.copy(color = if (enabled) WColor.text else WColor.faint, fontWeight = FontWeight.Bold)
        )
    }
}
