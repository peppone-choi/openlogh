package com.openlogh.gateway.dto

data class AttachWorldRequest(
    val baseUrl: String,
)

data class WorldRouteResponse(
    val worldId: Long,
    val baseUrl: String,
)
