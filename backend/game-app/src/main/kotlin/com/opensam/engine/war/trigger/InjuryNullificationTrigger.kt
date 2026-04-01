package com.opensam.engine.war.trigger

import com.opensam.engine.war.BattleTriggerContext
import com.opensam.engine.war.WarUnitTrigger
import com.opensam.engine.war.WarUnitTriggerRegistry

/**
 * che_부상무효: Injury nullification trigger.
 * Legacy: 견고 special grants injury immunity.
 *
 * onEngagementStart: Sets injuryImmune = true regardless of opponent type.
 * Existing Che견고Trigger (BattleTrigger) also sets injuryImmune -- both firing
 * is idempotent (same boolean set to true).
 */
object InjuryNullificationTrigger : WarUnitTrigger {
    override val code = "che_부상무효"
    override val priority = 10

    init {
        WarUnitTriggerRegistry.register(this)
    }

    override fun onEngagementStart(ctx: BattleTriggerContext): BattleTriggerContext {
        ctx.injuryImmune = true
        ctx.battleLogs.add("부상무효 발동! 부상에 면역이다!")
        return ctx
    }
}
