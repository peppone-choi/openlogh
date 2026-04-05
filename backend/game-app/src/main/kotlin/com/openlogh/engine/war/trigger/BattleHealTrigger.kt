package com.openlogh.engine.war.trigger

import com.openlogh.engine.war.BattleTriggerContext
import com.openlogh.engine.war.WarUnitOfficer
import com.openlogh.engine.war.WarUnitTrigger
import com.openlogh.engine.war.WarUnitTriggerRegistry
import kotlin.math.floor

/**
 * che_전투치료시도 + che_전투치료발동: Battle healing trigger.
 * Legacy: prob=0.4 per phase after damage. On activation:
 * - Reduces received damage by 30% (multiply by 0.7, floor)
 * - Clears own injury to 0
 */
object BattleHealTrigger : WarUnitTrigger {
    override val code = "che_의술"
    override val priority = 10

    init {
        WarUnitTriggerRegistry.register(this)
    }

    override fun onPostDamage(ctx: BattleTriggerContext): BattleTriggerContext {
        // 시도 (attempt): 40% probability
        if (ctx.rng.nextDouble() >= 0.4) return ctx

        // 발동 (activation)
        ctx.defenderDamage = floor(ctx.defenderDamage * 0.7).toInt()
        if (ctx.attacker is WarUnitOfficer) {
            ctx.attacker.injury = 0
        }
        ctx.battleLogs.add("의술 발동! 피해를 줄이고 부상을 회복했다!")
        return ctx
    }
}
