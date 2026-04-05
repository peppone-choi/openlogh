package com.openlogh.engine.war.trigger

import com.openlogh.engine.war.BattleTriggerContext
import com.openlogh.engine.war.WarUnitOfficer
import com.openlogh.engine.war.WarUnitTrigger
import com.openlogh.engine.war.WarUnitTriggerRegistry

/**
 * che_저격시도 + che_저격발동: Sniping trigger.
 * Legacy: prob=0.5 on new opponent. On activation:
 * - Applies wound (snipeWoundAmount 20-40) to defender general
 * - Grants +20 morale boost
 */
object SnipingTrigger : WarUnitTrigger {
    override val code = "che_저격"
    override val priority = 15

    init {
        WarUnitTriggerRegistry.register(this)
    }

    override fun onEngagementStart(ctx: BattleTriggerContext): BattleTriggerContext {
        if (ctx.snipeImmune) return ctx
        if (ctx.defender !is WarUnitOfficer) return ctx
        if (!ctx.newOpponent) return ctx

        // 시도 (attempt): 50% probability
        if (ctx.rng.nextDouble() >= 0.5) return ctx

        // 발동 (activation)
        ctx.snipeActivated = true
        ctx.snipeWoundAmount = ctx.rng.nextInt(20, 41)  // [20, 40] inclusive
        ctx.moraleBoost += 20
        ctx.battleLogs.add("저격 발동! 적장에게 부상을 입혔다!")
        return ctx
    }
}
