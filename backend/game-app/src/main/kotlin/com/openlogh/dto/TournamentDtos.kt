package com.openlogh.dto

data class TournamentRegisterRequest(val officerId: Long)

data class CreateTournamentRequest(val type: Int)

data class AdvanceTournamentRequest(val tournamentId: Long)

data class PlaceBetRequest(val officerId: Long, val targetId: Long, val amount: Int)

data class TournamentBracketMatchResponse(
    val round: Int,
    val match: Int,
    val p1: Long,
    val p2: Long,
    val winner: Long? = null,
)

data class TournamentInfoResponse(
    val state: Int,
    val bracket: List<TournamentBracketMatchResponse>,
    val participants: List<Long>,
)

data class BetEntryResponse(
    val officerId: Long,
    val targetId: Long,
    val amount: Int,
)

data class BettingInfoResponse(
    val bets: List<BetEntryResponse>,
    val odds: Map<String, Double>,
)
