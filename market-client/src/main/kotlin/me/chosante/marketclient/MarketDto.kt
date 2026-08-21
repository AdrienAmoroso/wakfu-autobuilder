package me.chosante.marketclient

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.chosante.common.I18nText
import me.chosante.common.Rarity

@Serializable
data class ObservationResponse(
    val id: Int,
    val itemId: Int,
    val server: String,
    val observedAt: String,
    val source: String,
    val confidenceScore: Double,
    val minPrice: Long,
    val avgPrice: Long,
    val medianPrice: Long? = null,
    val lotSize: Int? = null,
    val quantityAvailable: Int? = null,
    val rawPayload: String? = null,
    val comment: String? = null,
    val elements: String? = null,
    val isDiscovered: Boolean? = null,
    val runeSlots: String? = null,
    val captureUid: String? = null,
)

@Serializable
data class CreateObservationRequest(
    val itemId: Int,
    val server: String,
    val observedAt: String,
    val source: String,
    val confidenceScore: Double,
    val minPrice: Long,
    val avgPrice: Long,
    val medianPrice: Long? = null,
    val comment: String? = null,
)

@Serializable
data class UpdatePricesRequest(
    val minPrice: Long,
    val avgPrice: Long,
    val medianPrice: Long? = null,
)

@Serializable
enum class FlagMotif {
    @SerialName("parsing_error")
    PARSING_ERROR,

    @SerialName("outlier")
    OUTLIER,

    @SerialName("duplicate")
    DUPLICATE,

    @SerialName("manual_check")
    MANUAL_CHECK,
}

@Serializable
data class FlagRequest(
    val motif: FlagMotif,
)

@Serializable
data class IngredientCost(
    val itemId: Int,
    val quantity: Int,
    val unitPrice: Long? = null,
    val subtotal: Long? = null,
)

@Serializable
data class CraftCostResponse(
    val itemId: Int,
    val craftCost: Long? = null,
    val marketPrice: Long? = null,
    val grossMargin: Long? = null,
    val netMargin: Long? = null,
    val roi: Double? = null,
    val confidence: Double,
    val missingPriceCount: Int,
    val decision: String,
    val ingredients: List<IngredientCost>,
)

// Mirrors market-server's own DTO exactly -- see its doc comment for why the shape is unified
// across equipment and encyclopedia-sourced (resource/consumable) items.
@Serializable
data class ItemInfoResponse(
    val itemId: Int,
    val name: I18nText,
    val level: Int,
    val rarity: Rarity,
    val iconKey: Int,
    val category: String,
    val isEquipment: Boolean,
)

@Serializable
data class ItemSearchResult(
    val item: ItemInfoResponse,
    val latestMinPrice: Long? = null,
    val latestAvgPrice: Long? = null,
    val latestServer: String? = null,
    val latestObservedAt: String? = null,
)

// phase: "idle" | "capturing" | "processing" | "error" -- mirrors market-server's own DTO exactly.
@Serializable
data class CaptureStatusResponse(
    val phase: String,
    val sessionName: String? = null,
    val startedAt: Long? = null,
    val message: String? = null,
    val lastImportedCount: Int? = null,
)
