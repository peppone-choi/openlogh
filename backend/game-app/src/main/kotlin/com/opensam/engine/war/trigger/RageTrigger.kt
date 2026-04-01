package com.opensam.engine.war.trigger

import com.opensam.engine.war.BattleTriggerContext
import com.opensam.engine.war.WarUnitTrigger
import com.opensam.engine.war.WarUnitTriggerRegistry

/**
 * che_격노시도 + che_격노발동: Rage trigger.
 * Legacy: Activates on opponent critical/dodge, accumulating per phase.
 * warPower multiplier = 1 + 0.2 * activationCount
 * 50% chance of extra phase per activation.
 *
 * Activation conditions:
 * - After opponent critical hit: 50% chance
 * - After opponent dodge: 25% chance
 * - Blocked by suppressActive (진압)
 */
object RageTrigger : WarUnitTrigger {
    override val code = "che_격노"
    override val priority = 10

    init {
        WarUnitTriggerRegistry.register(this)
    }

    override fun onPostDamage(ctx: BattleTriggerContext): BattleTriggerContext {
        if (ctx.suppressActive) return ctx

        // React to opponent critical
        if (ctx.criticalActivated && ctx.rng.nextDouble() < 0.5) {
            activateRage(ctx, reactedToCritical = true)
        }

        // React to opponent dodge
        if (ctx.dodgeActivated && ctx.rng.nextDouble() < 0.25) {
            activateRage(ctx, reactedToCritical = false)
        }

        return ctx
    }

    override fun onPreAttack(ctx: BattleTriggerContext): BattleTriggerContext {
        if (ctx.rageActivationCount > 0) {
            // Legacy: warPower = 1 + 0.2 * activatedSkillCount('격노')
            ctx.attackMultiplier *= 1.0 + 0.2 * ctx.rageActivationCount
        }
        return ctx
    }

    private fun activateRage(ctx: BattleTriggerContext, reactedToCritical: Boolean) {
        ctx.rageActivationCount += 1
        ctx.rageDamageStack = 0.2 * ctx.rageActivationCount

        if (ctx.rng.nextDouble() < 0.5) {
            ctx.rageExtraPhases += 1
            ctx.battleLogs.add(
                if (reactedToCritical) "격노 발동! 상대 필살에 진노하여 추가 페이즈를 얻었다!"
                else "격노 발동! 상대 회피 시도에 진노하여 추가 페이즈를 얻었다!"
            )
        } else {
            ctx.battleLogs.add(
                if (reactedToCritical) "격노 발동! 상대 필살에 분노가 쌓인다!"
                else "격노 발동! 상대 회피 시도에 분노가 쌓인다!"
            )
        }
    }
}
