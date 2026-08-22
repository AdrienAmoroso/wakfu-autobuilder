package me.chosante.itemsextractor

import me.chosante.common.I18nText

// The item's flavor text lives verbatim in this meta tag on both EN and FR detail pages -- confirmed
// against real fetched pages: identical to the visible on-page "Description" panel, but a single-line
// attribute value needs no HTML-section isolation the way MonsterDropParser.parseDrops does.
private val metaDescriptionRegex = Regex("""<meta name="description" content="([^"]*)"""")

/**
 * One item's flavor-text description, fetched from its own EN + (if available) FR detail page.
 * Resumable/cached via [client] like every other crawl in this module -- one request per language
 * per item, ~2x the page count of the drops crawl for the same item set. Returns `null` when the
 * item's description is blank (a real, not uncommon, in-game state -- not every item has flavor
 * text) or its detail page couldn't be fetched. es/pt fall back to the English text, matching how
 * [ListingRow] names are already handled in `Main.kt` -- the encyclopedia has no es/pt locale.
 */
suspend fun crawlItemDescription(
    client: EncyclopediaClient,
    itemId: Int,
    enHref: String,
    frHref: String?,
): I18nText? {
    val enDetail = client.fetch("${EncyclopediaClient.BASE}$enHref", cacheKey = "item-detail-en-$itemId") ?: return null
    val enDesc =
        metaDescriptionRegex
            .find(enDetail)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.ifBlank { null } ?: return null

    val frDesc =
        frHref
            ?.let { client.fetch("${EncyclopediaClient.BASE}$it", cacheKey = "item-detail-fr-$itemId") }
            ?.let {
                metaDescriptionRegex
                    .find(it)
                    ?.groupValues
                    ?.get(1)
                    ?.trim()
                    ?.ifBlank { null }
            }
            ?: enDesc

    return I18nText(fr = frDesc, en = enDesc, es = enDesc, pt = enDesc)
}
