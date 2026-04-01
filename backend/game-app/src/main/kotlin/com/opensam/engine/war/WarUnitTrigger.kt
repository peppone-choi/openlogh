package com.opensam.engine.war

/**
 * War-unit-level trigger for runtime battle-phase hooks.
 *
 * Separate from BattleTrigger (fine-grained roll hooks) and ActionModifier (stat-only).
 * WarUnitTrigger handles coarser battle-phase hooks:
 * - onEngagementStart: once per new opponent (legacy: getBattleInitSkillTriggerList)
 * - onPreAttack: per phase before attack roll (legacy: getBattlePhaseSkillTriggerList)
 * - onPostDamage: per phase after damage applied
 * - onPostRound: after all phases with one opponent complete
 *
 * Plan 02 will add concrete implementations (intimidation, sniping, healing, rage).
 */
interface WarUnitTrigger {
    val code: String
    val priority: Int

    /** Fired once per new opponent engagement (legacy: getBattleInitSkillTriggerList). */
    fun onEngagementStart(ctx: BattleTriggerContext): BattleTriggerContext = ctx

    /** Fired per phase before attack roll (legacy: getBattlePhaseSkillTriggerList). */
    fun onPreAttack(ctx: BattleTriggerContext): BattleTriggerContext = ctx

    /** Fired per phase after damage applied. */
    fun onPostDamage(ctx: BattleTriggerContext): BattleTriggerContext = ctx

    /** Fired after all phases with one opponent complete. */
    fun onPostRound(ctx: BattleTriggerContext): BattleTriggerContext = ctx
}

object WarUnitTriggerRegistry {
    private val triggers = mutableMapOf<String, WarUnitTrigger>()

    fun register(trigger: WarUnitTrigger) {
        triggers[trigger.code] = trigger
    }

    fun get(code: String): WarUnitTrigger? = triggers[code]

    fun allCodes(): Set<String> = triggers.keys
}
