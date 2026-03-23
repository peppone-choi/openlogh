package com.openlogh.engine.tactical

sealed class BattleEvent(val turn: Int, val type: String) {

    data class MoveEvent(
        val eventTurn: Int,
        val unitId: Int,
        val fleetId: Long,
        val fromX: Double,
        val fromY: Double,
        val fromZ: Double,
        val toX: Double,
        val toY: Double,
        val toZ: Double,
        val distance: Double,
    ) : BattleEvent(eventTurn, "MOVE")

    data class AttackEvent(
        val eventTurn: Int,
        val attackerUnitId: Int,
        val attackerFleetId: Long,
        val targetUnitId: Int,
        val targetFleetId: Long,
        val weaponType: String,
        val damage: Int,
        val hit: Boolean,
        val critical: Boolean,
        val targetHpBefore: Int,
        val targetHpAfter: Int,
        val distance: Double = 0.0,
    ) : BattleEvent(eventTurn, "ATTACK")

    data class DestroyEvent(
        val eventTurn: Int,
        val unitId: Int,
        val fleetId: Long,
        val destroyedBy: Int,
    ) : BattleEvent(eventTurn, "DESTROY")

    data class MoraleChangeEvent(
        val eventTurn: Int,
        val fleetId: Long,
        val oldMorale: Int,
        val newMorale: Int,
        val reason: String,
    ) : BattleEvent(eventTurn, "MORALE")

    data class FormationChangeEvent(
        val eventTurn: Int,
        val fleetId: Long,
        val oldFormation: Formation,
        val newFormation: Formation,
    ) : BattleEvent(eventTurn, "FORMATION")

    data class EnergyChangeEvent(
        val eventTurn: Int,
        val fleetId: Long,
        val oldEnergy: EnergyAllocation,
        val newEnergy: EnergyAllocation,
    ) : BattleEvent(eventTurn, "ENERGY")

    data class RetreatEvent(
        val eventTurn: Int,
        val fleetId: Long,
        val forced: Boolean,
    ) : BattleEvent(eventTurn, "RETREAT")

    data class SpecialEvent(
        val eventTurn: Int,
        val fleetId: Long,
        val specialCode: String,
        val description: String,
    ) : BattleEvent(eventTurn, "SPECIAL")
}
