package me.chosante.ui.manualbuild

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.chosante.common.skills.CharacterSkills
import me.chosante.ui.i18n.Tr
import me.chosante.ui.i18n.tr
import me.chosante.ui.manualbuild.tabs.ManualEnchantmentTab
import me.chosante.ui.manualbuild.tabs.ManualItemsTab
import me.chosante.ui.manualbuild.tabs.ManualNoteTab
import me.chosante.ui.paperdoll.ItemBar
import me.chosante.ui.spells.ClassSpellsPanel
import me.chosante.ui.state.BuildSearchModel
import me.chosante.ui.state.ManualTab
import me.chosante.ui.state.ZenithState
import me.chosante.ui.state.asManualView
import me.chosante.ui.state.copyManualZenithLink
import me.chosante.ui.state.exportBuild
import me.chosante.ui.state.openManualItemsTabForSlot
import me.chosante.ui.state.openManualZenithBuild
import me.chosante.ui.state.requestSaveBuild
import me.chosante.ui.state.resetManualBuild
import me.chosante.ui.state.setManualNote
import me.chosante.ui.state.setManualSkills
import me.chosante.ui.state.setManualTab
import me.chosante.ui.state.unequipManualItem
import me.chosante.ui.theme.WColor
import me.chosante.ui.theme.WDimens
import me.chosante.ui.theme.WTypography

/**
 * A second, solver-independent way to reach a build (à la Zenith/Wakfuli), rebuilt to match the
 * community build-planner layout the user asked for: a top horizontal item strip, a left sidebar of
 * grouped stats, and a 5-tab center panel (Items / Enchantment / Aptitude / Sort / Note). No CP-SAT
 * search involved (contrast [me.chosante.ui.shell.AppShell]'s auto-search Builder screen).
 *
 * Reuses existing machinery throughout rather than a parallel UI: [ItemBar] and
 * [ManualStatSidebar]/[ClassSpellsPanel] all render off [me.chosante.ui.state.UiState.asManualView]'s
 * throwaway projection of `manualBuild`, and its modal traffic (only the shared
 * [me.chosante.common.RuneType] picker now -- equip/sublimation/skill are inline, no modal) is served
 * by the SAME [me.chosante.ui.components.ModalHost] instance [me.chosante.ui.shell.AppShell] renders,
 * which resolves its manual-vs-auto wiring by [me.chosante.ui.state.UiState.screen].
 */
@Composable
fun ManualBuildScreen(model: BuildSearchModel) {
    val ui = model.ui
    val manualUi = ui.asManualView()
    Column(modifier = Modifier.fillMaxSize().background(WColor.bg)) {
        ManualBuildToolbar(model = model)
        Box(modifier = Modifier.fillMaxWidth().background(WColor.surface).border(1.dp, WColor.hairline)) {
            ItemBar(
                ui = manualUi,
                onUnequip = model::unequipManualItem,
                onSlotClick = model::openManualItemsTabForSlot
            )
        }
        Row(modifier = Modifier.fillMaxSize().background(WColor.hairline)) {
            Box(modifier = Modifier.width(300.dp).fillMaxHeight().background(WColor.bg)) {
                ManualStatSidebar(ui = ui)
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight().background(WColor.bg)) {
                Row(modifier = Modifier.fillMaxWidth().padding(WDimens.gap)) {
                    ManualBuildTabStrip(current = ui.manualActiveTab, onSelect = model::setManualTab)
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (ui.manualActiveTab) {
                        ManualTab.ITEMS -> ManualItemsTab(ui = ui, equipmentCatalog = model.equipmentCatalog, model = model, modifier = Modifier.fillMaxSize())
                        ManualTab.ENCHANTMENT -> ManualEnchantmentTab(ui = ui, model = model, modifier = Modifier.fillMaxSize())
                        ManualTab.APTITUDE ->
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(WDimens.gap)
                            ) {
                                ManualSkillTree(
                                    skills = manualUi.build?.characterSkills ?: CharacterSkills(ui.level),
                                    onChange = model::setManualSkills
                                )
                            }
                        ManualTab.SPELLS -> ClassSpellsPanel(ui = manualUi, modifier = Modifier.fillMaxSize())
                        ManualTab.NOTE -> ManualNoteTab(note = ui.manualNote, onNoteChange = model::setManualNote, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualBuildToolbar(model: BuildSearchModel) {
    val ui = model.ui
    Row(
        modifier = Modifier.fillMaxWidth().background(WColor.surface).padding(horizontal = WDimens.pad, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = ui.manualActiveBuildName ?: tr(Tr.MANUAL_UNSAVED_BUILD),
            style = WTypography.bodyMedium.copy(color = WColor.text, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f)
        )
        ToolbarButton(text = tr(Tr.MANUAL_NEW_BUILD), onClick = model::resetManualBuild)
        ToolbarButton(
            text = if (ui.manualActiveBuildId != null) tr(Tr.UPDATE_BUILD) else tr(Tr.SAVE_BUILD),
            onClick = model::requestSaveBuild,
            filled = true
        )
        ToolbarButton(
            text = if (ui.manualZenithState == ZenithState.Loading) tr(Tr.OPENING) else tr(Tr.OPEN_IN_ZENITH),
            onClick = model::openManualZenithBuild,
            enabled = ui.manualZenithState != ZenithState.Loading
        )
        ToolbarButton(text = tr(Tr.COPY_BUILD_LINK), onClick = model::copyManualZenithLink, enabled = ui.manualZenithState != ZenithState.Loading)
        ToolbarButton(text = tr(Tr.EXPORT_BUILD), onClick = model::exportBuild)
    }
}

@Composable
private fun ToolbarButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    filled: Boolean = false,
) {
    Box(
        modifier =
            Modifier
                .height(34.dp)
                .alpha(if (enabled) 1f else 0.5f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (filled) WColor.accent else Color.Transparent)
                .border(1.dp, if (filled) WColor.accent else WColor.border, RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, style = WTypography.labelMedium.copy(color = if (filled) WColor.bg else WColor.text))
    }
}
