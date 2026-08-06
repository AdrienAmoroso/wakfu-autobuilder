package me.chosante.marketserver.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
