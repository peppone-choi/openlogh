package com.openlogh.engine.tactical

enum class OrderType {
    MOVE,
    ATTACK,
    FORMATION_CHANGE,
    ENERGY_CHANGE,
    RETREAT,
    SPECIAL,
}

data class TacticalOrder(
    val officerId: Long,
    val unitId: Int? = null,
    val type: OrderType,
    val targetX: Double? = null,
    val targetY: Double? = null,
    val targetZ: Double? = null,
    val targetUnitId: Int? = null,
    val formation: Formation? = null,
    val energy: EnergyAllocation? = null,
    val specialCode: String? = null,
)
