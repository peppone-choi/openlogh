package com.openlogh.command.general

import com.openlogh.command.CommandServices
import com.openlogh.engine.modifier.DomesticContext
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Utility functions for domestic commands, ported from legacy PHP.
 * Shared by DomesticCommand and standalone domestic commands (정착장려, 주민선정, 기술연구).
 */
object DomesticUtils {

    fun statBonus(statValue: Int, weight: Double = 1.0): Double {
        return 1.0 + (statValue - 50) / 500.0 * weight
    }

    fun getDomesticExpLevelBonus(expLevel: Int): Double {
        return 1.0 + expLevel / 500.0
    }

    /**
     * Legacy: CriticalRatioDomestic($general, $statKey)
     *
     * Computes success/fail ratios based on avg(leadership, strength, intel) / stat ratio.
     * Higher ratio (stat is low relative to avg) => higher success AND fail chance.
     *
     * @return Pair(successRatio, failRatio)
     */
    fun criticalRatioDomestic(general: Officer, statKey: String): Pair<Double, Double> {
        val leadership = general.leadership.toDouble()
        val strength = general.command.toDouble()
        val intel = general.intelligence.toDouble()
        val avg = (leadership + strength + intel) / 3.0

        val statValue = when (statKey) {
            "leadership" -> leadership
            "strength" -> strength
            "intel" -> intel
            else -> intel
        }

        val ratio = min(avg / statValue, 1.2)

        var fail = (ratio / 1.2).pow(1.4) - 0.3
        var success = (ratio / 1.2).pow(1.5) - 0.25

        fail = fail.coerceIn(0.0, 0.5)
        success = success.coerceIn(0.0, 0.5)

        return Pair(success, fail)
    }

    /**
     * Legacy: CriticalScoreEx($rng, $pick)
     *
     * Returns a random multiplier:
     *   success => rng(2.2, 3.0)
     *   fail    => rng(0.2, 0.4)
     *   normal  => 1.0
     */
    fun criticalScoreEx(rng: Random, pick: String): Double {
        return when (pick) {
            "success" -> 2.2 + rng.nextDouble() * 0.8  // [2.2, 3.0)
            "fail" -> 0.2 + rng.nextDouble() * 0.2      // [0.2, 0.4)
            else -> 1.0
        }
    }

    /**
     * Weighted random choice matching PHP $rng->choiceUsingWeight().
     */
    fun choiceUsingWeight(rng: Random, weights: Map<String, Double>): String {
        val total = weights.values.sum()
        if (total <= 0) return weights.keys.first()
        var roll = rng.nextDouble() * total
        for ((key, weight) in weights) {
            roll -= weight
            if (roll <= 0) return key
        }
        return weights.keys.last()
    }

    /**
     * Apply onCalcDomestic modifier for a specific varType via the modifier system.
     */
    fun applyModifier(
        services: CommandServices?,
        general: Officer,
        nation: Faction?,
        actionKey: String,
        varType: String,
        baseValue: Double
    ): Double {
        val modifierService = services?.modifierService ?: return baseValue
        val modifiers = modifierService.getModifiers(general, nation)
        if (modifiers.isEmpty()) return baseValue

        val baseCtx = when (varType) {
            "cost" -> DomesticContext(costMultiplier = baseValue, actionCode = actionKey)
            "success" -> DomesticContext(successMultiplier = baseValue, actionCode = actionKey)
            "fail" -> DomesticContext(failMultiplier = baseValue, actionCode = actionKey)
            "score" -> DomesticContext(scoreMultiplier = baseValue, actionCode = actionKey)
            "rice" -> DomesticContext(riceMultiplier = baseValue, actionCode = actionKey)
            "train" -> DomesticContext(trainMultiplier = baseValue, actionCode = actionKey)
            "atmos" -> DomesticContext(atmosMultiplier = baseValue, actionCode = actionKey)
            else -> return baseValue
        }

        val modified = modifierService.applyDomesticModifiers(modifiers, baseCtx)
        return when (varType) {
            "cost" -> modified.costMultiplier
            "success" -> modified.successMultiplier
            "fail" -> modified.failMultiplier
            "score" -> modified.scoreMultiplier
            "rice" -> modified.riceMultiplier
            "train" -> modified.trainMultiplier
            "atmos" -> modified.atmosMultiplier
            else -> baseValue
        }
    }
}
