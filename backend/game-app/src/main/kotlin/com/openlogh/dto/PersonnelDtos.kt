package com.openlogh.dto

data class RankLadderEntryDto(
    val officerId: Long,
    val name: String,
    val rankTier: Int,
    val rankTitle: String,
    val rankTitleKo: String,
    val meritPoints: Int,
    val famePoints: Int,
    val totalStats: Int,
)

data class PersonnelInfoDto(
    val officerId: Long,
    val name: String,
    val rankTier: Int,
    val rankTitle: String,
    val rankTitleKo: String,
    val meritPoints: Int,
    val evaluationPoints: Int,
    val famePoints: Int,
    val promotionEligible: Boolean,
    val nextRankTitle: String?,
    val nextRankTitleKo: String?,
)

data class PromoteDemoteRequest(
    val officerId: Long,
)

data class PersonnelActionResponse(
    val success: Boolean,
    val message: String,
    val updatedOfficer: PersonnelInfoDto?,
)
