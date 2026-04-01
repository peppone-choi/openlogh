package com.opensam.engine.war.trigger

import com.opensam.engine.war.BattleTriggerContext
import com.opensam.engine.war.WarUnitTrigger
import com.opensam.engine.war.WarUnitTriggerRegistry

/**
 * che_필살강화_회피불가: Unavoidable critical trigger.
 * Legacy: 필살 special's undodgeable aspect.
 *
 * onPreAttack: Sets dodgeDisabled = true every phase (no probability gate).
 * Existing Che필살Trigger (BattleTrigger) handles critical damage amplification
 * via onPostCritical -- this WarUnitTrigger adds the "회피불가" aspect only.
 */
object UnavoidableCriticalTrigger : WarUnitTrigger {
    override val code = "che_필살강화_회피불가"
    override val priority = 10

    init {
        WarUnitTriggerRegistry.register(this)
    }

    override fun onPreAttack(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.dodgeDisabled = true
        ctx.battleLogs.add("필살강화 발동! 회피 불가!")
        return ctx
    }
}
