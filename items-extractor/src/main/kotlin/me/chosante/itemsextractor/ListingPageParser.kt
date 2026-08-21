package me.chosante.itemsextractor

/** One row of an encyclopedia category listing table (icon / name / rarity / level per item). */
data class ListingRow(
    val itemId: Int,
    val name: String,
    val rarityIndex: Int,
    val level: Int,
    val iconUrl: String,
)

/**
 * Parses an Ankama encyclopedia category listing page (`.../resources?page=N`,
 * `.../consumables?page=N`, and their French equivalents) -- a plain HTML `<table>`, one `<tr>` per
 * item, columns icon/name+rarity/type/level. Confirmed against real fetched pages (see the research
 * behind this extractor): every row carries a `linker_item_<id>` marker, an `ak-rarity-<0..7>` badge
 * with the rarity name as its `title`, a hosted icon `<img src>`, and an `item-level">Lvl <n>` cell.
 * Regex, not a real HTML parser, matching this repo's existing scraper (spells-extractor) -- the row
 * shape is simple and stable enough that a full parser dependency isn't worth adding.
 */
object ListingPageParser {
    // Isolate each row's own inner HTML first (non-greedy, DOTALL) so the field regexes below can
    // never bleed into a neighbouring row or trailing page content (e.g. a "related items" widget).
    private val rowRegex = Regex("""<tr class="ak-bg-(?:odd|even)"[^>]*>(.*?)</tr>""", RegexOption.DOT_MATCHES_ALL)
    private val idRegex = Regex("""linker_item_(\d+)""")
    private val iconRegex = Regex("""<img src="(https://static\.ankama\.com/[^"]+)"""")
    private val rarityRegex = Regex("""ak-rarity-(\d+)"""")
    private val nameRegex = Regex("""ak-rarity-\d+"[^>]*></span><span class="ak-linker"><a href="[^"]*">([^<]+)</a>""")

    // "Lvl 231" on English pages, "Niv. 231" on French ones -- the French listing pages are only
    // crawled for names (see Main.kt), but this must still match there or the whole row is dropped
    // (every field is required), silently losing every French name.
    private val levelRegex = Regex("""item-level">(?:Lvl|Niv\.)\s*(\d+)""")

    fun parseRows(html: String): List<ListingRow> =
        rowRegex
            .findAll(html)
            .mapNotNull { match ->
                val row = match.groupValues[1]
                val id =
                    idRegex
                        .find(row)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: return@mapNotNull null
                val icon = iconRegex.find(row)?.groupValues?.get(1) ?: return@mapNotNull null
                val rarityIndex =
                    rarityRegex
                        .find(row)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: return@mapNotNull null
                val name =
                    nameRegex
                        .find(row)
                        ?.groupValues
                        ?.get(1)
                        ?.trim()
                        ?.ifBlank { null } ?: return@mapNotNull null
                val level =
                    levelRegex
                        .find(row)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: return@mapNotNull null
                ListingRow(id, name, rarityIndex, level, icon)
            }.toList()

    /** The highest `?page=N` link on a listing page (its own page always included), or 1 if unpaginated. */
    fun maxPage(html: String): Int = Regex("""[?&]page=(\d+)""").findAll(html).mapNotNull { it.groupValues[1].toIntOrNull() }.maxOrNull() ?: 1
}
