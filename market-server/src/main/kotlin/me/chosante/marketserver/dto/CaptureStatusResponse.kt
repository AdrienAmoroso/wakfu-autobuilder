package me.chosante.marketserver.dto

import kotlinx.serialization.Serializable

// Plain string phase (not a serialized enum) so this DTO is trivially copyable into market-client
// without an enum-mapping step -- matches how the other cross-module DTOs already do it.
@Serializable
data class CaptureStatusResponse(
    val phase: String,
    val sessionName: String? = null,
    val startedAt: Long? = null,
    val message: String? = null,
    val lastImportedCount: Int? = null,
)
