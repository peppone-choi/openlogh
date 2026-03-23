package com.openlogh.engine.tactical

enum class VictoryType {
    ANNIHILATION,
    DECAPITATION,
    ROUT,
    TIME_LIMIT,
}

data class VictoryResult(
    val winnerFactionId: Long,
    val victoryType: VictoryType,
    val description: String,
)

data class TurnResult(
    val turn: Int,
    val events: List<BattleEvent>,
    val victory: VictoryResult? = null,
    val attackerFleetSummaries: List<FleetSummary>,
    val defenderFleetSummaries: List<FleetSummary>,
)

data class FleetSummary(
    val fleetId: Long,
    val officerName: String,
    val aliveUnits: Int,
    val totalUnits: Int,
    val totalHp: Int,
    val morale: Int,
    val formation: Formation,
)
