package io.eugene239.gnotifier

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val status: String,
    val uptimeMs: Long,
    val version: String,
)
