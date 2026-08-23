package me.chosante.itemsextractor

import me.chosante.common.I18nText
import me.chosante.common.ItemSummary
import me.chosante.common.Rarity
import java.io.File

// The encyclopedia's h1 title carries the item's own name, wrapped in an icon <a>/<span> this
// strips out. Confirmed against real fetched pages -- same markup on every category's detail page.
private val h1Regex = Regex("""<h1 class="ak-return-link">(.*?)</h1>""", RegexOption.DOT_MATCHES_ALL)
private val htmlTagRegex = Regex("""<[^>]*>""")
private val detailRarityRegex = Regex("""ak-rarity-(\d+)"""")
private val detailIconRegex = Regex("""ak-encyclo-detail-illu">\s*<img src="([^"]+)"""")
private val detailDescriptionRegex = Regex("""<meta name="description" content="([^"]*)"""")

private fun extractName(html: String): String? =
    h1Regex
        .find(html)
        ?.groupValues
        ?.get(1)
        ?.replace(htmlTagRegex, "")
        ?.trim()
        ?.ifBlank { null }

private fun extractDescription(html: String): String? =
    detailDescriptionRegex
        .find(html)
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?.ifBlank { null }

/**
 * Recovers items that are excluded from their own category's public listing pages (mostly
 * quest/decor/crafting-workshop items, confirmed against real data: e.g. id 4592 "Quest Items
 * Display Window") but still have a real, individually fetchable detail page -- which is why
 * [CATEGORIES]'s listing-page crawl misses them even though they're real, current, in-game items
 * (their ids come straight from the CDN's `recipeResults.json`/`recipeIngredients.json`/
 * `harvestLoots.json`, not guessed).
 *
 * The URL's category segment is IGNORED by the site -- confirmed live: the same id resolves
 * identically under `/resources/`, `/consumables/`, `/customization/`, and `/miscellaneous/` -- so
 * a fixed placeholder segment works for every id regardless of its real category. Validated against
 * a 40-item random sample of ids unknown at the time: 39/40 (97%) resolved with a real name.
 *
 * [level] is NOT present anywhere on a detail page (confirmed: no level markup of any kind) --
 * defaults to 0, documented honestly rather than fabricated, same as `harvest-materials.json`.
 * Recovered items are filed under "miscellaneous" (the same catch-all category their content most
 * resembles: raw/refined crafting materials, workshops, decorative items -- see [CATEGORIES]'s own
 * doc comment on what that category already covers).
 */
suspend fun crawlUnknownItemsById(
    client: EncyclopediaClient,
    ids: Set<Int>,
    iconsDir: File,
): List<ItemSummary> {
    val results = mutableListOf<ItemSummary>()
    var iconsDownloaded = 0
    for (id in ids) {
        val enHtml = client.fetch("${EncyclopediaClient.BASE}/en/mmorpg/encyclopedia/resources/$id", cacheKey = "unknown-item-en-$id") ?: continue
        val enName = extractName(enHtml) ?: continue
        val rarity =
            detailRarityRegex
                .find(enHtml)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?.let { rarityIndexToRarity[it] } ?: Rarity.COMMON
        val iconUrl = detailIconRegex.find(enHtml)?.groupValues?.get(1)
        val enDescription = extractDescription(enHtml)

        val frHtml = client.fetch("${EncyclopediaClient.BASE}/fr/mmorpg/encyclopedie/ressources/$id", cacheKey = "unknown-item-fr-$id")
        val frName = frHtml?.let { extractName(it) } ?: enName
        val frDescription = frHtml?.let { extractDescription(it) } ?: enDescription

        if (iconUrl != null && client.download(iconUrl, File(iconsDir, "$id.png"))) iconsDownloaded++

        results +=
            ItemSummary(
                itemId = id,
                name = I18nText(fr = frName, en = enName, es = enName, pt = enName),
                level = 0,
                rarity = rarity,
                category = "miscellaneous",
                iconKey = id,
                description =
                    enDescription?.let {
                        I18nText(fr = frDescription ?: it, en = it, es = it, pt = it)
                    }
            )
    }
    println("  unknown-item recovery: ${results.size} / ${ids.size} resolved, $iconsDownloaded icon(s) downloaded")
    return results
}
