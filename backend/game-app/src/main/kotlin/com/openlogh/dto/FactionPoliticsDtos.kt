package com.openlogh.dto

import java.time.OffsetDateTime

// ========== Overview ==========

data class FactionPoliticsOverviewDto(
    val factionId: Long,
    val factionType: String,
    val governanceType: String, // "autocracy", "democracy", "npc"
    val leaderName: String?,
    val leaderId: Long?,
    val councilStatus: CouncilStatusDto?,
    val activeCoup: CoupStatusDto?,
    val activeElection: ElectionDto?,
    val loans: List<LoanDto>,
    val fezzanOperational: Boolean,
)

// ========== Empire ==========

data class CoupStatusDto(
    val coupId: Long,
    val phase: String,
    val leaderId: Long,
    val leaderName: String,
    val supporterCount: Int,
    val politicalPower: Int,
    val threshold: Int,
    val startedAt: OffsetDateTime,
)

data class NobilityDto(
    val officerId: Long,
    val officerName: String,
    val rank: String,
    val nameKo: String,
    val politicsBonus: Float,
)

// ========== Alliance ==========

data class CouncilStatusDto(
    val seats: List<CouncilSeatDto>,
)

data class CouncilSeatDto(
    val seatCode: String,
    val nameKo: String,
    val officerId: Long?,
    val officerName: String?,
    val electedAt: OffsetDateTime?,
    val termEndAt: OffsetDateTime?,
)

data class ElectionDto(
    val electionId: Long,
    val type: String,
    val startedAt: OffsetDateTime,
    val candidates: List<CandidateDto>,
    val isCompleted: Boolean,
    val winnerId: Long?,
)

data class CandidateDto(
    val officerId: Long,
    val officerName: String,
    val votes: Int,
)

// ========== Fezzan ==========

data class LoanDto(
    val loanId: Long,
    val principal: Int,
    val interestRate: Float,
    val remainingDebt: Int,
    val issuedAt: OffsetDateTime,
    val dueAt: OffsetDateTime,
    val isDefaulted: Boolean,
)

data class IntelligenceOfferDto(
    val type: String,
    val nameKo: String,
    val cost: Int,
    val description: String,
)

// ========== Requests ==========

data class InitiateCoupRequest(val factionId: Long)
data class CastElectionVoteRequest(val electionId: Long, val candidateId: Long)
data class TakeLoanRequest(val amount: Int)
data class RepayLoanRequest(val loanId: Long, val amount: Int)
data class BuyIntelRequest(val targetFactionId: Long, val intelligenceType: String)
data class StartElectionRequest(val seatCode: String? = null)
