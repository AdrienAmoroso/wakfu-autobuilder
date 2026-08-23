package me.chosante.marketserver.equipment

import kotlinx.serialization.json.Json
import me.chosante.common.Equipment
import me.chosante.common.ItemSummary
import me.chosante.common.ItemType
import me.chosante.common.Rarity
import me.chosante.common.Sublimation
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

    // Sublimations -- first-party data (bdata-extractor, see AGENTS.md), keyed by their real in-game
    // item id (zenithId, used for Zenith's /shard/add -- confirmed non-zero for all 232 entries).
    // Optional for the same reason resourceItemsById is (an older build predating this file).
    private val sublimationsById: Map<Int, Sublimation> by lazy {
        val stream = ItemCatalog::class.java.getResourceAsStream("/sublimations.json")
        if (stream == null) {
            emptyMap()
        } else {
            json.decodeFromString<List<Sublimation>>(stream.bufferedReader().readText()).associateBy { it.zenithId }
        }
    }

    // Costume/Torch/Tool items -- real CDN equipment (name/level/rarity/icon) that EquipmentExtractor
    // deliberately excludes from equipments.json because they fill no character slot (see
    // EquipmentExtractor.extractNonCombatItems). Optional for the same reason resourceItemsById is
    // (an older build predating this file).
    private val equipmentAdjacentItemsById: Map<Int, ItemSummary> by lazy {
        val stream = ItemCatalog::class.java.getResourceAsStream("/equipment-adjacent-items.json")
        if (stream == null) {
            emptyMap()
        } else {
            json.decodeFromString<List<ItemSummary>>(stream.bufferedReader().readText()).associateBy { it.itemId }
        }
    }

    // Raw harvestable materials (ores, wood, fish, fruit…) -- see HarvestExtractor.extractHarvestMaterials
    // for how these are joined from CDN data harvest-extractor already fetches, not scraped. Optional
    // for the same reason resourceItemsById is (an older build predating this file).
    private val harvestMaterialsById: Map<Int, ItemSummary> by lazy {
        val stream = ItemCatalog::class.java.getResourceAsStream("/harvest-materials.json")
        if (stream == null) {
            emptyMap()
        } else {
            json.decodeFromString<List<ItemSummary>>(stream.bufferedReader().readText()).associateBy { it.itemId }
        }
    }

    // Equipment wins on an id collision with resource items -- shouldn't happen (the CDN equip feed
    // and the encyclopedia categories this extractor covers are disjoint, confirmed during research).
    // Sublimations are a *real*, NOT hypothetical, collision though: items-extractor's encyclopedia
    // crawl (resources/consumables/customization/miscellaneous) already independently picks up 228 of
    // the 232 known sublimations under one of those generic categories (confirmed against real data --
    // e.g. itemId 24130 "Inflexibility" is both a resource-items.json entry AND a sublimation).
    // sublimations.json is the richer, purpose-built, first-party source for those ids (accurate
    // rarity/kind/tier, not just a name/level scraped off the wrong listing page), so it wins over the
    // generic resource-items entry -- this also means most sublimations inherit a real icon for free
    // (items-extractor already downloaded it under the same id). Costumes are the same kind of real
    // collision (cosmetics also show up under the encyclopedia's "customization" category) --
    // equipment-adjacent-items.json's precise torch/tool/costume label wins over that generic one.
    // Harvest materials are the same again -- resource-items.json's "resource" category can overlap
    // with the same ids -- and harvest-materials.json is the more precise, first-party source.
    private val all: List<ItemInfoResponse> by lazy {
        val equipmentIds = equipmentById.keys
        val sublimationIds = sublimationsById.keys
        val equipmentAdjacentIds = equipmentAdjacentItemsById.keys
        val harvestMaterialIds = harvestMaterialsById.keys
        equipmentById.values.map { it.toItemInfoResponse() } +
            sublimationsById.values.filterNot { it.zenithId in equipmentIds }.map { it.toItemInfoResponse() } +
            equipmentAdjacentItemsById.values.map { it.toItemInfoResponse() } +
            harvestMaterialsById.values.map { it.toItemInfoResponse() } +
            resourceItemsById.values
                .filterNot {
                    it.itemId in equipmentIds || it.itemId in sublimationIds || it.itemId in equipmentAdjacentIds || it.itemId in harvestMaterialIds
                }.map { it.toItemInfoResponse() }
    }

    fun findById(id: Int): ItemInfoResponse? =
        equipmentById[id]?.toItemInfoResponse()
            ?: sublimationsById[id]?.toItemInfoResponse()
            ?: equipmentAdjacentItemsById[id]?.toItemInfoResponse()
            ?: harvestMaterialsById[id]?.toItemInfoResponse()
            ?: resourceItemsById[id]?.toItemInfoResponse()

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
        isEquipment = true,
        characteristics = characteristics,
        itemType = itemType,
        maxShardSlots = maxShardSlots
    )

private fun ItemSummary.toItemInfoResponse() =
    ItemInfoResponse(
        itemId = itemId,
        name = name,
        level = level,
        rarity = rarity,
        iconKey = iconKey,
        category = category,
        isEquipment = false,
        description = description
    )

// Sublimations aren't leveled (no character-level requirement) -- 0 is honest, not a placeholder for
// missing data. rarity (SublimationRarity) is a slot-type classifier (epic/relic dedicated slot vs.
// normal socket), not a display rarity -- gameRarity is the real in-game Rarity, sourced from the CDN
// the same way equipment's is (see Sublimation.gameRarity's doc comment).
private fun Sublimation.toItemInfoResponse() =
    ItemInfoResponse(
        itemId = zenithId,
        name = name,
        level = 0,
        rarity = gameRarity,
        iconKey = zenithId,
        category = "sublimation",
        isEquipment = false,
        sublimation = this
    )
