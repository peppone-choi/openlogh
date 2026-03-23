package com.openlogh.engine.trigger

import com.openlogh.entity.Officer

class TriggerCaller {
    private val triggers = linkedMapOf<String, ObjectTrigger>()

    fun addTrigger(trigger: ObjectTrigger) {
        triggers[trigger.uniqueId] = trigger
    }

    fun fire(env: TriggerEnv) {
        val sorted = triggers.values.sortedBy { it.priority.ordinal }
        for (trigger in sorted) {
            val continueChain = trigger.action(env)
            if (!continueChain) {
                env.stopNextAction = true
                break
            }
        }
    }

    fun isEmpty(): Boolean = triggers.isEmpty()
    fun size(): Int = triggers.size
}

fun buildPreTurnTriggers(officer: Officer): List<ObjectTrigger> {
    return listOf(InjuryReductionTrigger(officer))
}

fun applyDomesticModifiers(
    triggers: List<GeneralTrigger>,
    general: Officer,
    turnType: String,
    varType: String,
    value: Double,
    aux: Map<String, Any> = emptyMap(),
): Double {
    var result = value
    for (trigger in triggers.sortedBy { it.priority.ordinal }) {
        result = trigger.onCalcDomestic(general, turnType, varType, result, aux)
    }
    return result
}

fun applyStatModifiers(
    triggers: List<GeneralTrigger>,
    general: Officer,
    statName: String,
    value: Double,
    aux: Map<String, Any> = emptyMap(),
): Double {
    var result = value
    for (trigger in triggers.sortedBy { it.priority.ordinal }) {
        result = trigger.onCalcStat(general, statName, result, aux)
    }
    return result
}
