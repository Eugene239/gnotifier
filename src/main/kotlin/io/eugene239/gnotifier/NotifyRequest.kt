package io.eugene239.gnotifier

import kotlinx.serialization.Serializable

@Serializable
data class NotifyRequest(
    val message: String,
)
