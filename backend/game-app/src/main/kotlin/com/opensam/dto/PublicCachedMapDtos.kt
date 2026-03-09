package com.opensam.dto

import java.time.OffsetDateTime

data class PublicCachedMapCityResponse(
    val id: Long,
    val name: String,
    val x: Int,
    val y: Int,
    val level: Int,
    val region: Int? = null,
    val nationName: String,
    val nationColor: String,
    val isCapital: Boolean = false,
    val supplyState: Int = 1,
    val state: Int = 0,
)

data class PublicCachedMapHistoryResponse(
    val id: Long,
    val sentAt: OffsetDateTime,
    val text: String,
    val year: Int? = null,
    val month: Int? = null,
    val events: List<String>? = null,
)

data class PublicWorldSummary(
    val id: Long,
    val name: String,
)

data class PublicCachedMapResponse(
    val available: Boolean,
    val worldId: Long?,
    val worldName: String?,
    val mapCode: String?,
    val currentYear: Int? = null,
    val currentMonth: Int? = null,
    val cities: List<PublicCachedMapCityResponse>,
    val history: List<PublicCachedMapHistoryResponse>,
    val worlds: List<PublicWorldSummary> = emptyList(),
)
