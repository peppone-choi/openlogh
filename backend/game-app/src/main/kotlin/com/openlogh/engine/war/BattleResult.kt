package com.openlogh.engine.war

data class BattleResult(
    val attackerWon: Boolean,
    val cityOccupied: Boolean,
    val attackerDamageDealt: Int,
    val defenderDamageDealt: Int,
)
