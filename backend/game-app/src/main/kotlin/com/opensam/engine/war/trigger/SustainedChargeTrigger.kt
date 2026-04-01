package com.opensam.engine.war.trigger

import com.opensam.engine.war.BattleTriggerContext
import com.opensam.engine.war.WarUnitTrigger
import com.opensam.engine.war.WarUnitTriggerRegistry

/**
 * che_돌격지속: Sustained charge trigger.
 * Legacy: Extends war phases when attacker is winning.
 *
 * onPostDamage: If attacker HP > defender HP (winning), 40% chance to add +1 bonusPhases.
 * BattleEngine consumes bonusPhases to extend the phase loop.
 */
object SustainedChargeTrigger : WarUnitTrigger {
    override val code = "che_돌격지속"
    override val priority = 10

    init {
        WarUnitTriggerRegistry.register(this)
    }

    override fun onPostDamage(ctx: BattleTriggerContext): BattleTriggerContext {
        // Only fires when attacker is winning (more HP)
        if (ctx.attacker.hp <= ctx.defender.hp) return ctx

        // 40% probability
        if (ctx.rng.nextDouble() >= 0.4) return ctx

        // 발동 (activation)
        ctx.bonusPhases += 1
        ctx.battleLogs.add("돌격지속 발동! 추가 페이즈를 얻었다!")
        return ctx
    }
}
