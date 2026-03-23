package com.openlogh.engine.tactical

import java.util.UUID

enum class BattlePhase {
    SETUP,
    COMBAT,
    RESULT,
}

data class TacticalBattleSession(
    val sessionCode: String = UUID.randomUUID().toString(),
    val sessionId: Long,
    val planetId: Long,
    val attackerFleets: MutableList<TacticalFleet>,
    val defenderFleets: MutableList<TacticalFleet>,
    val grid: TacticalGrid = TacticalGrid(),
    var currentTurn: Int = 0,
    val maxTurns: Int = 100,
    var phase: BattlePhase = BattlePhase.SETUP,
    val pendingOrders: MutableMap<Long, MutableList<TacticalOrder>> = mutableMapOf(),
    val battleLog: MutableList<BattleEvent> = mutableListOf(),
    var groundAssault: GroundAssaultState = GroundAssaultState(),
) {
    fun allAttackerUnits(): List<TacticalUnit> =
        attackerFleets.flatMap { it.units }

    fun allDefenderUnits(): List<TacticalUnit> =
        defenderFleets.flatMap { it.units }

    fun allUnits(): List<TacticalUnit> =
        allAttackerUnits() + allDefenderUnits()

    fun findFleet(fleetId: Long): TacticalFleet? =
        attackerFleets.firstOrNull { it.fleetId == fleetId }
            ?: defenderFleets.firstOrNull { it.fleetId == fleetId }

    fun findUnit(unitId: Int): TacticalUnit? =
        allUnits().firstOrNull { it.id == unitId }

    fun findFleetForUnit(unitId: Int): TacticalFleet? {
        for (fleet in attackerFleets + defenderFleets) {
            if (fleet.units.any { it.id == unitId }) return fleet
        }
        return null
    }

    fun isAttacker(factionId: Long): Boolean =
        attackerFleets.any { it.factionId == factionId }

    fun submitOrders(officerId: Long, orders: List<TacticalOrder>) {
        pendingOrders[officerId] = orders.toMutableList()
    }

    fun clearPendingOrders() {
        pendingOrders.clear()
    }

    fun isFinished(): Boolean = phase == BattlePhase.RESULT
}
