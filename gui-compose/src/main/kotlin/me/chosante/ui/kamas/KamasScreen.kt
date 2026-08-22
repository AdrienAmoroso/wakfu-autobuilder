package me.chosante.ui.kamas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.chosante.marketclient.CraftCostResponse
import me.chosante.marketclient.HarvestOpportunity
import me.chosante.marketclient.ItemInfoResponse
import me.chosante.marketclient.MonsterFarmingOpportunity
import me.chosante.ui.components.Hairline
import me.chosante.ui.components.InfoTip
import me.chosante.ui.components.ItemBadge
import me.chosante.ui.components.ItemThumbnail
import me.chosante.ui.components.MessageCard
import me.chosante.ui.components.MonsterIcon
import me.chosante.ui.components.StatLine
import me.chosante.ui.components.TabButton
import me.chosante.ui.components.localized
import me.chosante.ui.i18n.Lang
import me.chosante.ui.i18n.Tr
import me.chosante.ui.i18n.craftDecisionLabel
import me.chosante.ui.i18n.tr
import me.chosante.ui.state.KamasTab
import me.chosante.ui.state.MarketState
import me.chosante.ui.state.UiState
import me.chosante.ui.theme.WColor
import me.chosante.ui.theme.WDimens
import me.chosante.ui.theme.WTypography

/**
 * "How do I make kamas right now" -- three independently-ranked opportunity lists (Crafting /
 * Harvesting / Monster Farming), each sourced from real captured HDV prices via market-server. The
 * three are deliberately NOT merged into one cross-category ranking: there's no reliable
 * time-per-action data for a craft vs. a harvest vs. a kill, so a single "kamas/hour" number across
 * them would be invented, not computed. See `AGENTS.md`/the Kamas plan for the full rationale.
 */
@Composable
fun KamasScreen(
    ui: UiState,
    onSelectTab: (KamasTab) -> Unit,
    onRequestItemInfo: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(WColor.bg)) {
        KamasTabHeader(current = ui.kamasTab, onSelect = onSelectTab)
        Box(modifier = Modifier.fillMaxSize().padding(WDimens.pad)) {
            when (ui.kamasTab) {
                KamasTab.CRAFTING -> CraftingTab(ui = ui, onRequestItemInfo = onRequestItemInfo)
                KamasTab.HARVESTING -> HarvestingTab(ui = ui)
                KamasTab.MONSTER_FARMING -> MonsterFarmingTab(ui = ui)
            }
        }
    }
}

@Composable
private fun KamasTabHeader(
    current: KamasTab,
    onSelect: (KamasTab) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(WColor.bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(WColor.bg)
                        .border(1.dp, WColor.border, RoundedCornerShape(8.dp))
                        .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                TabButton(label = tr(Tr.KAMAS_TAB_CRAFTING), selected = current == KamasTab.CRAFTING) { onSelect(KamasTab.CRAFTING) }
                TabButton(label = tr(Tr.KAMAS_TAB_HARVESTING), selected = current == KamasTab.HARVESTING) { onSelect(KamasTab.HARVESTING) }
                TabButton(
                    label = tr(Tr.KAMAS_TAB_MONSTER_FARMING),
                    selected = current == KamasTab.MONSTER_FARMING
                ) { onSelect(KamasTab.MONSTER_FARMING) }
            }
        }
        Hairline()
    }
}

@Composable
private fun CraftingTab(
    ui: UiState,
    onRequestItemInfo: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = tr(Tr.KAMAS_CRAFTING_SUBTITLE), style = WTypography.bodySmall.copy(color = WColor.muted))
            if (ui.craftOpportunitiesState == MarketState.Loading) {
                Text(text = tr(Tr.KAMAS_SCANNING_RECIPES), style = WTypography.labelSmall.copy(color = WColor.muted))
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        when {
            ui.craftOpportunities.isNotEmpty() ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ui.craftOpportunities, key = { it.itemId }) { opportunity ->
                        CraftOpportunityRow(
                            opportunity = opportunity,
                            item = ui.itemInfoCache[opportunity.itemId],
                            lang = ui.lang,
                            onRequestItemInfo = onRequestItemInfo
                        )
                    }
                }

            ui.craftOpportunitiesState == MarketState.Error ->
                MessageCard(title = tr(Tr.CANT_REACH_MARKET_SERVER), hint = ui.error ?: tr(Tr.START_MARKET_SERVER_HINT))

            ui.craftOpportunitiesState == MarketState.Ready ->
                MessageCard(title = tr(Tr.KAMAS_NO_CRAFTS_TITLE), hint = tr(Tr.KAMAS_NO_CRAFTS_HINT))
        }
    }
}

@Composable
private fun CraftOpportunityRow(
    opportunity: CraftCostResponse,
    item: ItemInfoResponse?,
    lang: Lang,
    onRequestItemInfo: (Int) -> Unit,
) {
    val decisionColor = if (opportunity.decision == "craft") WColor.success else WColor.muted
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(WColor.surface)
                .border(1.dp, WColor.hairline, RoundedCornerShape(9.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ItemBadge(
            itemId = opportunity.itemId,
            item = item,
            lang = lang,
            onRequestItemInfo = onRequestItemInfo,
            modifier = Modifier.widthIn(min = 220.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            StatLine(
                tr(Tr.ROI_LABEL),
                opportunity.roi?.let { "${(it * 100).toInt()}%" } ?: "—",
                hint = tr(Tr.ROI_HINT)
            )
            StatLine(tr(Tr.NET_MARGIN), opportunity.netMargin?.toString() ?: "—", hint = tr(Tr.NET_MARGIN_HINT))
        }
        Column(modifier = Modifier.weight(1f)) {
            StatLine(tr(Tr.CRAFT_COST_LABEL), opportunity.craftCost.toString(), hint = tr(Tr.CRAFT_COST_HINT))
            StatLine(tr(Tr.MARKET_PRICE_LABEL), opportunity.marketPrice?.toString() ?: "—")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = craftDecisionLabel(opportunity.decision, lang),
                style = WTypography.labelMedium.copy(color = decisionColor),
                fontWeight = FontWeight.Bold
            )
            InfoTip(text = tr(Tr.CRAFT_DECISION_HINT))
        }
    }
}

@Composable
private fun HarvestingTab(ui: UiState) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = tr(Tr.KAMAS_HARVESTING_SUBTITLE), style = WTypography.bodySmall.copy(color = WColor.muted))
            if (ui.harvestOpportunitiesState == MarketState.Loading) {
                Text(text = tr(Tr.KAMAS_SCANNING_NODES), style = WTypography.labelSmall.copy(color = WColor.muted))
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        when {
            ui.harvestOpportunities.isNotEmpty() ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ui.harvestOpportunities, key = { it.node.resourceId }) { opportunity ->
                        HarvestOpportunityRow(opportunity = opportunity, lang = ui.lang)
                    }
                }

            ui.harvestOpportunitiesState == MarketState.Error ->
                MessageCard(title = tr(Tr.CANT_REACH_MARKET_SERVER), hint = ui.error ?: tr(Tr.START_MARKET_SERVER_HINT))

            ui.harvestOpportunitiesState == MarketState.Ready ->
                MessageCard(title = tr(Tr.KAMAS_NO_HARVEST_TITLE), hint = tr(Tr.KAMAS_NO_HARVEST_HINT))
        }
    }
}

@Composable
private fun HarvestOpportunityRow(
    opportunity: HarvestOpportunity,
    lang: Lang,
) {
    val node = opportunity.node
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(WColor.surface)
                .border(1.dp, WColor.hairline, RoundedCornerShape(9.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ItemThumbnail(iconKey = node.iconKey, size = 32.dp)
        Column(modifier = Modifier.widthIn(min = 220.dp)) {
            Text(text = node.name.localized(lang), style = WTypography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = tr(Tr.KAMAS_NODE_CATEGORY_SKILL).format(node.category, node.skillLevelRequired),
                style = WTypography.labelSmall.copy(color = WColor.muted)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            StatLine(
                tr(Tr.EXPECTED_VALUE_LABEL),
                opportunity.expectedValue?.toString() ?: "—",
                hint = tr(Tr.KAMAS_HARVEST_EXPECTED_VALUE_HINT)
            )
            val pricedDrops = node.drops.size - opportunity.missingDropCount
            StatLine(
                tr(Tr.PRICED_DROPS_LABEL),
                "$pricedDrops / ${node.drops.size}",
                hint = tr(Tr.PRICED_DROPS_HINT)
            )
        }
    }
}

@Composable
private fun MonsterFarmingTab(ui: UiState) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = tr(Tr.KAMAS_MONSTER_FARMING_SUBTITLE), style = WTypography.bodySmall.copy(color = WColor.muted))
            if (ui.monsterFarmingOpportunitiesState == MarketState.Loading) {
                Text(text = tr(Tr.KAMAS_SCANNING_MONSTERS), style = WTypography.labelSmall.copy(color = WColor.muted))
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        when {
            ui.monsterFarmingOpportunities.isNotEmpty() ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ui.monsterFarmingOpportunities, key = { it.monster.id }) { opportunity ->
                        MonsterFarmingRow(opportunity = opportunity, lang = ui.lang)
                    }
                }

            ui.monsterFarmingOpportunitiesState == MarketState.Error ->
                MessageCard(title = tr(Tr.CANT_REACH_MARKET_SERVER), hint = ui.error ?: tr(Tr.START_MARKET_SERVER_HINT))

            ui.monsterFarmingOpportunitiesState == MarketState.Ready ->
                MessageCard(title = tr(Tr.KAMAS_NO_MONSTER_TITLE), hint = tr(Tr.KAMAS_NO_MONSTER_HINT))
        }
    }
}

@Composable
private fun MonsterFarmingRow(
    opportunity: MonsterFarmingOpportunity,
    lang: Lang,
) {
    val monster = opportunity.monster
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(WColor.surface)
                .border(1.dp, WColor.hairline, RoundedCornerShape(9.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MonsterIcon(monster = monster, size = 32.dp)
        Column(modifier = Modifier.widthIn(min = 220.dp)) {
            Text(text = monster.name.localized(lang), style = WTypography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = tr(Tr.KAMAS_MONSTER_LEVEL).format(monster.level), style = WTypography.labelSmall.copy(color = WColor.muted))
        }
        Column(modifier = Modifier.weight(1f)) {
            StatLine(
                tr(Tr.EXPECTED_VALUE_LABEL),
                opportunity.expectedValue?.toString() ?: "—",
                hint = tr(Tr.KAMAS_MONSTER_EXPECTED_VALUE_HINT)
            )
            if (opportunity.missingDropCount > 0) {
                StatLine(
                    tr(Tr.UNPRICED_DROPS_LABEL),
                    opportunity.missingDropCount.toString(),
                    hint = tr(Tr.UNPRICED_DROPS_HINT)
                )
            }
        }
    }
}
