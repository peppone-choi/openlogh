package com.openlogh.engine.trigger

import com.openlogh.entity.Officer

data class TriggerEnv(
    val worldId: Long,
    val year: Int,
    val month: Int,
    val generalId: Long,
    val vars: MutableMap<String, Any> = mutableMapOf(),
    var stopNextAction: Boolean = false,
)

enum class TriggerPriority {
    BEGIN, POST, FINAL
}

interface ObjectTrigger {
    val uniqueId: String
    val priority: TriggerPriority
    fun action(env: TriggerEnv): Boolean
}

interface GeneralTrigger : ObjectTrigger {
    fun onCalcDomestic(
        general: Officer,
        turnType: String,
        varType: String,
        value: Double,
        aux: Map<String, Any> = emptyMap(),
    ): Double = value

    fun onCalcStat(
        general: Officer,
        statName: String,
        value: Double,
        aux: Map<String, Any> = emptyMap(),
    ): Double = value
}
