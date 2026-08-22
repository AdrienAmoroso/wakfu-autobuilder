package me.chosante.itemsextractor

/**
 * Parses the encyclopedia's monster pages -- a different shape from [ListingPageParser]'s item
 * category tables (monsters carry no rarity/level column on the listing, and detail pages need the
 * full slugged URL; an id-only URL doesn't reliably serve monster content, confirmed against a real
 * fetch, unlike items/resources). Names/levels for display come from the already-committed
 * `monsters.json` (bdata-extractor) instead of being re-scraped here -- this crawl's only job is
 * resolving monsterId -> its drop table.
 */
object MonsterDropParser {
    // Isolate each row's own inner HTML FIRST (same non-greedy DOTALL approach ListingPageParser
    // uses), then extract id/href from within that bounded chunk -- a single page-wide regex
    // (id marker ... nearest following href) silently mispairs id with a NEIGHBOURING row's href
    // whenever a row's own href isn't the nearest one after its id marker (confirmed by a real
    // fetch: id=2 "Black Gobbly" paired with a Tofu-family monster's href, corrupting every drop
    // scraped for it downstream).
    private val listingRowRegex = Regex("""<tr class="ak-bg-(?:odd|even)"[^>]*>(.*?)</tr>""", RegexOption.DOT_MATCHES_ALL)
    private val idRegex = Regex("""linker_monster_(\d+)""")
    private val hrefRegex = Regex("""href="(/en/mmorpg/encyclopedia/monsters/[^"]+)"""")

    /** (monsterId, full detail-page href) pairs from one `/encyclopedia/monsters?page=N` listing page. */
    fun parseListingRow(html: String): List<Pair<Int, String>> =
        listingRowRegex
            .findAll(html)
            .mapNotNull { match ->
                val row = match.groupValues[1]
                val id =
                    idRegex
                        .find(row)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: return@mapNotNull null
                val href = hrefRegex.find(row)?.groupValues?.get(1) ?: return@mapNotNull null
                id to href
            }.distinctBy { it.first }
            .toList()

    // Bounds the drops-section search between its own id and the next `id="ak-encyclo-` section
    // (or a generous fallback window if this is the page's last section) -- the encyclopedia's
    // sections aren't cleanly delimited any other way via regex.
    private val dropItemIdRegex = Regex("""linker_item_(\d+)""")

    // Rate is NOT always a whole number -- confirmed against a real page (monsterId 2016, "Lanco
    // Plumo" at "0.8%"): rare equipment drops commonly use a decimal rate. An integer-only `(\d+)%`
    // silently dropped every such entry (an undercount, not a crash -- exactly the class of silent
    // data loss this crawler's honesty discipline exists to avoid).
    private val dropPercentRegex = Regex("""<span>\s*(\d+(?:\.\d+)?)%""")

    /**
     * (itemId, dropRate 0-1) pairs from a monster detail page's drops section, empty if it drops
     * nothing. Splits on each `ak-list-element` marker FIRST (confirmed against a real page: this
     * section can hold trailing entries with no percentage at all -- e.g. related items -- and a
     * single page-wide "id ... nearest following percent" regex would silently pair one of those
     * with a LATER, unrelated entry's percentage; the exact class of bug that corrupted
     * [parseListingRow] before it was row-isolated the same way).
     */
    fun parseDrops(html: String): List<Pair<Int, Double>> {
        val start = html.indexOf("ak-encyclo-monster-drops")
        if (start < 0) return emptyList()
        val nextSectionStart = html.indexOf("id=\"ak-encyclo-", start + 30)
        val section = if (nextSectionStart > 0) html.substring(start, nextSectionStart) else html.substring(start, minOf(html.length, start + 30_000))
        return section.split("ak-list-element").drop(1).mapNotNull { chunk ->
            val itemId =
                dropItemIdRegex
                    .find(chunk)
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull() ?: return@mapNotNull null
            val percent =
                dropPercentRegex
                    .find(chunk)
                    ?.groupValues
                    ?.get(1)
                    ?.toDoubleOrNull() ?: return@mapNotNull null
            itemId to (percent / 100.0)
        }
    }
}
