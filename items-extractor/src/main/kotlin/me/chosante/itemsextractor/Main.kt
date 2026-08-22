package me.chosante.itemsextractor

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import me.chosante.common.I18nText
import me.chosante.common.ItemSummary
import me.chosante.common.Rarity
import me.chosante.common.findRepositoryRoot
import java.io.File

/** English/French URL segments for one encyclopedia category, and the [ItemSummary.category] label. */
private data class CategoryRef(
    val label: String,
    val enSlug: String,
    val frSlug: String,
)

// The four categories that trade on the HDV outside of Equipment's coverage (gear, runes,
// sublimations, pets/mounts/emblems -- see EquipmentCatalog). "customization" (mostly account-bound
// cosmetics) and "miscellaneous" (mostly quest items) are included for catalog completeness/
// filtering (the Kamas/Market screens' category filter) even though many of their items never trade.
private val CATEGORIES =
    listOf(
        CategoryRef("resource", "resources", "ressources"),
        CategoryRef("consumable", "consumables", "consommables"),
        CategoryRef("customization", "customization", "personnalisation"),
        CategoryRef("miscellaneous", "miscellaneous", "divers")
    )

private val rarityIndexToRarity =
    mapOf(
        0 to Rarity.COMMON,
        1 to Rarity.UNCOMMON,
        2 to Rarity.RARE,
        3 to Rarity.MYTHIC,
        4 to Rarity.LEGENDARY,
        5 to Rarity.RELIC,
        6 to Rarity.SOUVENIR,
        7 to Rarity.EPIC
    )

/**
 * Builds two files by crawling Ankama's public encyclopedia (no CDN gamedata feed covers either):
 * - `autobuilder/src/main/resources/resource-items.json`: the item categories [ItemSummary] exists
 *   to cover -- resources, consumables, cosmetics and misc items, none of which have an
 *   equipment-style CDN feed (see `ItemSummary`'s doc comment and this module's research trail for
 *   why). Also downloads
 *   each item's hosted icon straight into `gui-compose/src/main/resources/assets/items/<itemId>.png`.
 * - `autobuilder/src/main/resources/monster-drops.json`: every listed monster's drop table -- see
 *   `MonsterDropCrawler.kt`.
 *
 * Resumable: every fetched page is cached under `items-extractor/.cache/`.
 *
 * Run with: `./gradlew :items-extractor:run`.
 */
suspend fun main() {
    val repoRoot = findRepositoryRoot()
    val client = EncyclopediaClient(cacheDir = File(repoRoot, "items-extractor/.cache"))

    println("Priming encyclopedia session…")
    client.prime()

    val allItems = mutableListOf<ItemSummary>()
    val iconsDir = File(repoRoot, "gui-compose/src/main/resources/assets/items")
    var iconsDownloaded = 0
    var iconsFailed = 0

    for (category in CATEGORIES) {
        val enRows = mutableMapOf<Int, ListingRow>()
        val frNames = mutableMapOf<Int, String>()

        val firstEnPage =
            client.fetch(
                "${EncyclopediaClient.BASE}/en/mmorpg/encyclopedia/${category.enSlug}?page=1",
                cacheKey = "${category.label}-en-1"
            )
        if (firstEnPage == null) {
            System.err.println("! ${category.label}: EN page 1 unreachable; skipping category")
            continue
        }
        val maxPage = ListingPageParser.maxPage(firstEnPage)
        ListingPageParser.parseRows(firstEnPage).forEach { enRows[it.itemId] = it }

        for (page in 2..maxPage) {
            val html =
                client.fetch(
                    "${EncyclopediaClient.BASE}/en/mmorpg/encyclopedia/${category.enSlug}?page=$page",
                    cacheKey = "${category.label}-en-$page"
                ) ?: continue
            ListingPageParser.parseRows(html).forEach { enRows[it.itemId] = it }
        }

        for (page in 1..maxPage) {
            val html =
                client.fetch(
                    "${EncyclopediaClient.BASE}/fr/mmorpg/encyclopedie/${category.frSlug}?page=$page",
                    cacheKey = "${category.label}-fr-$page"
                ) ?: continue
            ListingPageParser.parseRows(html).forEach { frNames[it.itemId] = it.name }
        }

        var skippedRarity = 0
        for (row in enRows.values) {
            val rarity = rarityIndexToRarity[row.rarityIndex]
            if (rarity == null) {
                skippedRarity++
                System.err.println("  ! ${category.label} #${row.itemId} (${row.name}): unknown rarity index ${row.rarityIndex}, skipped")
                continue
            }
            val enName = row.name
            val frName = frNames[row.itemId] ?: enName
            allItems +=
                ItemSummary(
                    itemId = row.itemId,
                    name = I18nText(fr = frName, en = enName, es = enName, pt = enName),
                    level = row.level,
                    rarity = rarity,
                    category = category.label,
                    iconKey = row.itemId
                )
            if (client.download(row.iconUrl, File(iconsDir, "${row.itemId}.png"))) {
                iconsDownloaded++
            } else {
                iconsFailed++
            }
        }

        println(
            "  ${category.label}: ${enRows.size} items across $maxPage page(s), ${frNames.size} FR names resolved" +
                if (skippedRarity > 0) ", $skippedRarity skipped (unknown rarity)" else ""
        )
    }

    val outputDir = File(repoRoot, "autobuilder/src/main/resources").apply { mkdirs() }
    val outputFile = File(outputDir, "resource-items.json")
    outputFile.writeText(Json.encodeToString(ListSerializer(ItemSummary.serializer()), allItems.sortedBy { it.itemId }))

    println("\nWrote ${allItems.size} items -> ${outputFile.name}")
    println("Icons: $iconsDownloaded downloaded/already-present, $iconsFailed failed")

    println("\nCrawling the bestiary for drop tables…")
    crawlMonsterDrops(client, repoRoot)
}
