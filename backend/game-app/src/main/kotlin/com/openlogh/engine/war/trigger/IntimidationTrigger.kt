package com.openlogh.engine.war.trigger

import com.openlogh.engine.war.BattleTriggerContext
import com.openlogh.engine.war.WarUnitGeneral
import com.openlogh.engine.war.WarUnitTrigger
import com.openlogh.engine.war.WarUnitTriggerRegistry

/**
 * che_위압시도 + che_위압발동: Intimidation trigger.
 * Legacy: prob=0.4 per engagement. On activation:
 * - Sets intimidated, disables dodge/critical/magic for defender
 * - Reduces defender atmos by 5
 * - Disables defender attack for 1 phase (intimidatePhasesRemaining = 1)
 */
object IntimidationTrigger : WarUnitTrigger {
    override val code = "che_위압"
    override val priority = 20

    init {
        WarUnitTriggerRegistry.register(this)
    }

    override fun onEngagementStart(ctx: BattleTriggerContext): BattleTriggerContext {
        if (ctx.defender !is WarUnitGeneral) return ctx

        // 시도 (attempt): 40% probability
        if (ctx.rng.nextDouble() >= 0.4) return ctx

        // 발동 (activation)
        ctx.intimidated = true
        ctx.dodgeDisabled = true
        ctx.criticalDisabled = true
        ctx.magicDisabled = true
        ctx.intimidatePhasesRemaining = 1
        ctx.defender.atmos = (ctx.defender.atmos - 5).coerceAtLeast(0)
        ctx.battleLogs.add("위압 발동! 적이 위축되었다!")
        return ctx
    }
}
