package com.openlogh.dto

data class OrgChartHolder(
    val positionType: String,
    val positionNameKo: String,
    val category: String,
    val officerId: Long?,
    val officerName: String?,
    val officerPicture: String?,
    val officerRank: Int?,
    val officerFactionId: Long?,
)

data class OrgChartResponse(
    val sessionId: Long,
    val holders: List<OrgChartHolder>,
    val allPositionTypes: List<PositionTypeInfo>,
)

data class PositionTypeInfo(
    val code: String,
    val displayName: String,
    val category: String,
    val minRank: Int,
    val grantedCommands: List<String>,
)

data class OfficerPositionCard(
    val id: Long,
    val positionType: String,
    val positionNameKo: String,
    val category: String,
    val grantedCommands: List<String>,
)
