package me.chosante.itemsextractor

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import me.chosante.common.HarvestDrop
import me.chosante.common.MonsterLoot
import java.io.File

private const val MONSTER_LISTING_BASE = "${EncyclopediaClient.BASE}/en/mmorpg/encyclopedia/monsters"

// The same detail page fetched for drops also carries the monster's 200x200 portrait, lazy-loaded
// via `data-src` -- confirmed against real cached pages: the filename is the monster's own `gfx` id
// (e.g. Black Gobbly, monsterId 2, gfx 100200002 -> ".../monster/200/100200002.png"), matching
// MonsterAssets.iconPath's `assets/monsters/<gfx>.png` lookup convention exactly, so no extra join
// against monsters.json is needed here.
private val portraitUrlRegex = Regex("""data-src="([^"]*/monster/200/(\d+)\.png)"""")

/**
 * Crawls the encyclopedia's bestiary for drop tables -- no CDN file covers this (confirmed dead
 * end: `quests.json`/`dungeons.json`/etc. all 403 under every plausible name), but each monster's
 * detail page has an explicit drops section. Writes `autobuilder/src/main/resources/monster-drops.json`.
 * Also backfills any missing portrait PNG under `gui-compose/src/main/resources/assets/monsters/`
 * for a monster this crawl surfaces (see [portraitUrlRegex]) -- portraits are otherwise a
 * hand-curated static set (see `MonsterAssets`'s doc comment) that only ever covered bosses, so an
 * ordinary monster newly surfaced here would have no portrait without this.
 *
 * Two passes: the listing pages (`?page=N`, ~36 of them) give (monsterId, detail page href) pairs --
 * a monster detail page needs its full slugged URL, an id-only URL doesn't reliably serve monster
 * content (confirmed against a real fetch, unlike items/resources). Then one fetch per monster for
 * its drops. Slower than the resources/consumables crawl (one page per monster, not one page per
 * ~24), but still bounded by the encyclopedia's own bestiary size (~860 listed monsters, not
 * `monsters.json`'s full 2846 -- the bdata table includes internal/unlisted variants the public site
 * doesn't carry a page for).
 */
suspend fun crawlMonsterDrops(
    client: EncyclopediaClient,
    repoRoot: File,
) {
    val firstPage = client.fetch("$MONSTER_LISTING_BASE?page=1", cacheKey = "monsters-listing-1")
    if (firstPage == null) {
        System.err.println("! monsters: listing page 1 unreachable; skipping monster-drop crawl entirely")
        return
    }
    val maxPage = ListingPageParser.maxPage(firstPage)

    val monsters = LinkedHashMap<Int, String>()
    MonsterDropParser.parseListingRow(firstPage).forEach { (id, href) -> monsters.putIfAbsent(id, href) }
    for (page in 2..maxPage) {
        val html = client.fetch("$MONSTER_LISTING_BASE?page=$page", cacheKey = "monsters-listing-$page") ?: continue
        MonsterDropParser.parseListingRow(html).forEach { (id, href) -> monsters.putIfAbsent(id, href) }
    }
    println("  monsters: $maxPage listing page(s), ${monsters.size} monsters found")

    val portraitsDir = File(repoRoot, "gui-compose/src/main/resources/assets/monsters")
    var portraitsDownloaded = 0
    var portraitsFailed = 0

    val loots = mutableListOf<MonsterLoot>()
    var withDrops = 0
    for ((id, href) in monsters) {
        val detailHtml = client.fetch("${EncyclopediaClient.BASE}$href", cacheKey = "monster-detail-$id") ?: continue
        val drops = MonsterDropParser.parseDrops(detailHtml)
        if (drops.isEmpty()) continue
        withDrops++
        loots += MonsterLoot(monsterId = id, drops = drops.map { (itemId, dropRate) -> HarvestDrop(itemId = itemId, dropRate = dropRate, quantity = 1) })

        val portrait = portraitUrlRegex.find(detailHtml)
        if (portrait != null) {
            val (url, gfx) = portrait.destructured
            if (client.download(url, File(portraitsDir, "$gfx.png"))) portraitsDownloaded++ else portraitsFailed++
        }
    }
    println("  monsters: $withDrops / ${monsters.size} have at least one drop")
    println("  monster portraits: $portraitsDownloaded downloaded/already-present, $portraitsFailed failed")

    val outputDir = File(repoRoot, "autobuilder/src/main/resources").apply { mkdirs() }
    val outputFile = File(outputDir, "monster-drops.json")
    outputFile.writeText(Json.encodeToString(ListSerializer(MonsterLoot.serializer()), loots.sortedBy { it.monsterId }))
    println("Wrote ${loots.size} monster loot tables -> ${outputFile.name}")
}
