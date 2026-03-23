package com.openlogh.command

data class CommandEnv(
    val year: Int = 200,
    val month: Int = 1,
    val startYear: Int = 190,
    val worldId: Long = 1,
    val realtimeMode: Boolean = false,
    val develCost: Int = 100,
    val scenario: Int = 0,
    val gameStor: MutableMap<String, Any> = mutableMapOf(),
)

fun CommandEnv.isTechLimited(currentTech: Double): Boolean {
    val band = (year - startYear) / 5 + 1
    return currentTech >= band * 1000.0
}
