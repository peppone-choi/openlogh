package com.openlogh.engine.trigger

import com.openlogh.entity.Officer

class InjuryReductionTrigger(private val officer: Officer) : ObjectTrigger {
    override val uniqueId = "injury_reduction"
    override val priority = TriggerPriority.BEGIN

    override fun action(env: TriggerEnv): Boolean {
        if (officer.injury > 0) {
            officer.injury = (officer.injury - 1).toShort()
            env.vars["injuryReduced"] = true
        }
        return true
    }
}

class TroopConsumptionTrigger(private val officer: Officer) : ObjectTrigger {
    override val uniqueId = "troop_consumption"
    override val priority = TriggerPriority.FINAL

    override fun action(env: TriggerEnv): Boolean {
        if (officer.ships <= 0) return true

        val consumption = maxOf(officer.ships / 100, 1)
        if (officer.supplies >= consumption) {
            officer.supplies -= consumption
        } else {
            officer.supplies = 0
            officer.morale = maxOf(0, officer.morale - 5).toShort()
            env.vars["troopStarving"] = true
        }
        return true
    }
}

class MedicineHealTrigger(
    private val officer: Officer,
    private val injuryTarget: Int = 10,
) : ObjectTrigger {
    override val uniqueId = "medicine_heal"
    override val priority = TriggerPriority.BEGIN

    override fun action(env: TriggerEnv): Boolean {
        if (officer.injury >= injuryTarget && officer.accessoryCode.contains("medicine")) {
            officer.injury = 0
            env.vars["medicineHealed"] = true
        }
        return true
    }
}
