package me.chosante.marketserver.equipment

import kotlinx.serialization.json.Json
import me.chosante.common.Equipment
import me.chosante.common.ItemSummary
import me.chosante.common.ItemType
import me.chosante.common.Rarity
import me.chosante.marketserver.dto.ItemInfoResponse

// equipments.json / resource-items.json reach this module's classpath via the extra
// `resources { srcDir(...) }` declared in market-server/build.gradle.kts (pointing at autobuilder's
// committed resource directory) -- not a project dependency on :autobuilder, and not on
// :items-extractor either (a maintainer-run tool, like :equipments-extractor, not a runtime dep).
object ItemCatalog {
    private val json = Json { ignoreUnknownKeys = true }

    private val equipmentById: Map<Int, Equipment> by lazy {
        val text =
            requireNotNull(ItemCatalog::class.java.getResourceAsStream("/equipments.json")) {
                "equipments.json not found on the classpath"
            }.bufferedReader().readText()
        json.decodeFromString<List<Equipment>>(text).associateBy { it.equipmentId }
    }

    // Resources/consumables from items-extractor's encyclopedia crawl -- optional (absent = an
    // items-extractor run hasn't happened yet), unlike equipments.json which every build ships.
    private val resourceItemsById: Map<Int, ItemSummary> by lazy {
        val stream = ItemCatalog::class.java.getResourceAsStream("/resource-items.json")
        if (stream == null) {
            emptyMap()
        } else {
            json.decodeFromString<List<ItemSummary>>(stream.bufferedReader().readText()).associateBy { it.itemId }
        }
    }

    // Equipment wins on an id collision -- shouldn't happen (the CDN equip feed and the encyclopedia
    // categories this extractor covers are disjoint, confirmed during research), but if it ever does,
    // the fuller, game-accurate source should win over the encyclopedia scrape.
    private val all: List<ItemInfoResponse> by lazy {
        val equipmentIds = equipmentById.keys
        equipmentById.values.map { it.toItemInfoResponse() } +
            resourceItemsById.values.filterNot { it.itemId in equipmentIds }.map { it.toItemInfoResponse() }
    }

    fun findById(id: Int): ItemInfoResponse? = equipmentById[id]?.toItemInfoResponse() ?: resourceItemsById[id]?.toItemInfoResponse()

    fun search(
        name: String?,
        minLevel: Int?,
        maxLevel: Int?,
        rarities: Set<Rarity>?,
        categories: Set<String>?,
        limit: Int,
    ): List<ItemInfoResponse> =
        all
            .asSequence()
            .filter { name.isNullOrBlank() || it.name.fr.contains(name, ignoreCase = true) || it.name.en.contains(name, ignoreCase = true) }
            .filter { minLevel == null || it.level >= minLevel }
            .filter { maxLevel == null || it.level <= maxLevel }
            .filter { rarities.isNullOrEmpty() || it.rarity in rarities }
            .filter { categories.isNullOrEmpty() || it.category in categories }
            .sortedWith(compareBy({ it.level }, { it.name.fr }))
            .take(limit)
            .toList()
}

// "creature" (Familiers/Montures) is split out from "equipment" here -- the HDV's own top-level
// category filter treats them separately even though they're equip-slot items sourced from the same
// CDN feed as gear.
private val CREATURE_ITEM_TYPES = setOf(ItemType.PETS, ItemType.MOUNTS)

private fun Equipment.toItemInfoResponse() =
    ItemInfoResponse(
        itemId = equipmentId,
        name = name,
        level = level,
        rarity = rarity,
        iconKey = guiId,
        category = if (itemType in CREATURE_ITEM_TYPES) "creature" else "equipment",
        isEquipment = true
    )

private fun ItemSummary.toItemInfoResponse() =
    ItemInfoResponse(itemId = itemId, name = name, level = level, rarity = rarity, iconKey = iconKey, category = category, isEquipment = false)
