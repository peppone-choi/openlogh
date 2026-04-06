package com.openlogh.dto

import com.openlogh.model.EnergyAllocation
import com.openlogh.model.Formation

// ── Request DTOs ──

data class BattleCommandRequest(
    val battleId: Long,
    val officerId: Long,
    val commandType: String,  // "energy", "formation", "retreat"
    val energy: Map<String, Int>? = null,
    val formation: String? = null,
)

// ── Response DTOs ──

data class TacticalBattleDto(
    val id: Long,
    val sessionId: Long,
    val starSystemId: Long,
    val attackerFactionId: Long,
    val defenderFactionId: Long,
    val phase: String,
    val startedAt: String,
    val endedAt: String? = null,
    val result: String? = null,
    val tickCount: Int,
    val attackerFleetIds: List<Long>,
    val defenderFleetIds: List<Long>,
    val units: List<TacticalUnitDto>,
)

data class TacticalUnitDto(
    val fleetId: Long,
    val officerId: Long,
    val officerName: String,
    val factionId: Long,
    val side: String,
    val posX: Double,
    val posY: Double,
    val hp: Int,
    val maxHp: Int,
    val ships: Int,
    val maxShips: Int,
    val training: Int,
    val morale: Int,
    val energy: Map<String, Int>,
    val formation: String,
    val commandRange: Double,
    val isAlive: Boolean,
    val isRetreating: Boolean,
    val retreatProgress: Double,
    val unitType: String,
)

data class BattleTickBroadcast(
    val battleId: Long,
    val tickCount: Int,
    val phase: String,
    val currentPhase: String = "MOVEMENT",
    val units: List<TacticalUnitDto>,
    val events: List<BattleTickEventDto>,
    val result: String? = null,
)

data class BattleTickEventDto(
    val type: String,
    val sourceUnitId: Long = 0,
    val targetUnitId: Long = 0,
    val value: Int = 0,
    val detail: String = "",
)

data class ActiveBattlesResponse(
    val battles: List<TacticalBattleDto>,
)
