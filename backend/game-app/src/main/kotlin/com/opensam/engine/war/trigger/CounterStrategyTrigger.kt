package com.opensam.engine.war.trigger

import com.opensam.engine.war.BattleTriggerContext
import com.opensam.engine.war.WarUnitTrigger
import com.opensam.engine.war.WarUnitTriggerRegistry

/**
 * che_반계: Counter-strategy phase-level trigger.
 * Legacy: getBattlePhaseSkillTriggerList for che_반계시도/che_반계발동.
 *
 * Logs attempt/activation per phase via onPreAttack.
 * Actual magic reflection stays in Che반계Trigger.onPreMagic (BattleTrigger).
 * 40% probability per phase.
 */
object CounterStrategyTrigger : WarUnitTrigger {
    override val code = "che_반계"
    override val priority = 10

    init {
        WarUnitTriggerRegistry.register(this)
    }

    override fun onPreAttack(ctx: BattleTriggerContext): BattleTriggerContext {
        // 시도 (attempt): 40% probability
        if (ctx.rng.nextDouble() >= 0.4) return ctx

        // 발동 (activation): log counter-strategy readiness
        ctx.battleLogs.add("반계 시도! 적의 계략에 대비한다!")
        return ctx
    }
}
