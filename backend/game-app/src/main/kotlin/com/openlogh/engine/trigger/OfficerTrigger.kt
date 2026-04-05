package com.openlogh.engine.trigger

import com.openlogh.engine.modifier.ActionModifier
import com.openlogh.engine.modifier.DomesticContext
import com.openlogh.engine.modifier.ItemModifiers
import com.openlogh.engine.modifier.StatContext
import com.openlogh.entity.Officer
import kotlin.random.Random

/**
 * General command triggers (legacy parity: OfficerTrigger/).
 *
 * These modify command behavior via onCalcDomestic hooks:
 *   - cost, rice, train, atmos, success, fail, score adjustments
 *
 * Fired by TurnExecutionHelper::preprocessCommand() before command runs.
 */
interface OfficerTrigger : ObjectTrigger {
    /**
     * Modify domestic command parameters.
     *
     * @param turnType command key (징병, 조달, 주민선정, etc.)
     * @param varType  parameter to modify (cost, rice, train, atmos, success, fail, score)
     * @param value    current value
     * @param aux      extra context (e.g., armType)
     * @return modified value
     */
    fun onCalcDomestic(
        general: Officer,
        turnType: String,
        varType: String,
        value: Double,
        aux: Map<String, Any> = emptyMap(),
    ): Double = value

    /**
     * Modify stat calculations.
     *
     * @param statName stat key (leadership, strength, intel, addDex, experience, etc.)
     * @param value    current value
     * @param aux      extra context
     * @return modified value
     */
    fun onCalcStat(
        general: Officer,
        statName: String,
        value: Double,
        aux: Map<String, Any> = emptyMap(),
    ): Double = value
}

// ========== Built-in General Triggers ==========

/**
 * 부상경감: Reduces injury by 1 each turn (priority BEGIN).
 * Legacy: OfficerTrigger/che_부상경감.php
 */
class InjuryReductionTrigger(private val general: Officer) : OfficerTrigger {
    override val uniqueId = "부상경감_${general.id}"
    override val priority = TriggerPriority.BEGIN

    override fun action(env: TriggerEnv): Boolean {
        if (general.injury > 0) {
            general.injury = (general.injury - 1).coerceIn(0, 80).toShort()
            env.vars["injuryReduced"] = true
        }
        return true
    }
}

/**
 * 병력군량소모: Consume rice for troops each turn (priority FINAL).
 * Legacy: OfficerTrigger/che_병력군량소모.php
 *
 * Rice consumption = crew / 100 (minimum 1 if crew > 0).
 * If not enough rice, crew loses atmos.
 */
class TroopConsumptionTrigger(private val general: Officer) : OfficerTrigger {
    override val uniqueId = "병력군량소모_${general.id}"
    override val priority = TriggerPriority.FINAL

    override fun action(env: TriggerEnv): Boolean {
        if (general.ships <= 0) return true

        val riceNeeded = maxOf(general.ships / 100, 1)
        if (general.supplies >= riceNeeded) {
            general.supplies -= riceNeeded
        } else {
            // Not enough rice - morale drops
            general.supplies = 0
            val atmosDrop = minOf(5, general.morale.toInt())
            general.morale = (general.morale - atmosDrop).coerceIn(0, 150).toShort()
            env.vars["troopStarving"] = true
        }
        return true
    }
}

class ModifierBridgeTrigger(
    private val general: Officer,
    private val modifiers: List<ActionModifier>,
) : OfficerTrigger {
    override val uniqueId = "modifier_bridge_${general.id}"
    override val priority = TriggerPriority.POST

    override fun action(env: TriggerEnv): Boolean = true

    override fun onCalcDomestic(
        general: Officer,
        turnType: String,
        varType: String,
        value: Double,
        aux: Map<String, Any>,
    ): Double {
        val baseCtx = when (varType) {
            "cost" -> DomesticContext(costMultiplier = value, actionCode = turnType)
            "success" -> DomesticContext(successMultiplier = value, actionCode = turnType)
            "fail" -> DomesticContext(failMultiplier = value, actionCode = turnType)
            "score" -> DomesticContext(scoreMultiplier = value, actionCode = turnType)
            "train" -> DomesticContext(trainMultiplier = value, actionCode = turnType)
            "atmos" -> DomesticContext(atmosMultiplier = value, actionCode = turnType)
            else -> return value
        }

        val modified = modifiers.fold(baseCtx) { ctx, modifier -> modifier.onCalcDomestic(ctx) }
        return when (varType) {
            "cost" -> modified.costMultiplier
            "success" -> modified.successMultiplier
            "fail" -> modified.failMultiplier
            "score" -> modified.scoreMultiplier
            "train" -> modified.trainMultiplier
            "atmos" -> modified.atmosMultiplier
            else -> value
        }
    }

    override fun onCalcStat(
        general: Officer,
        statName: String,
        value: Double,
        aux: Map<String, Any>,
    ): Double {
        val baseCtx = when (statName) {
            "leadership" -> StatContext(leadership = value)
            "strength" -> StatContext(strength = value)
            "intel" -> StatContext(intel = value)
            "criticalChance" -> StatContext(criticalChance = value)
            "dodgeChance" -> StatContext(dodgeChance = value)
            "magicChance" -> StatContext(magicChance = value)
            "warPower" -> StatContext(warPower = value)
            "bonusTrain" -> StatContext(bonusTrain = value)
            "bonusAtmos" -> StatContext(bonusAtmos = value)
            "magicTrialProb" -> StatContext(magicTrialProb = value)
            "magicSuccessProb" -> StatContext(magicSuccessProb = value)
            "magicSuccessDamage" -> StatContext(magicSuccessDamage = value)
            "dexMultiplier" -> StatContext(dexMultiplier = value)
            "expMultiplier" -> StatContext(expMultiplier = value)
            "injuryProb" -> StatContext(injuryProb = value)
            "initWarPhase" -> StatContext(initWarPhase = value)
            "sabotageDefence" -> StatContext(sabotageDefence = value)
            "dedicationMultiplier" -> StatContext(dedicationMultiplier = value)
            else -> return value
        }

        val modified = modifiers.fold(baseCtx) { stat, modifier -> modifier.onCalcStat(stat) }
        return when (statName) {
            "leadership" -> modified.leadership
            "strength" -> modified.command
            "intel" -> modified.intelligence
            "criticalChance" -> modified.criticalChance
            "dodgeChance" -> modified.dodgeChance
            "magicChance" -> modified.magicChance
            "warPower" -> modified.warPower
            "bonusTrain" -> modified.bonusTrain
            "bonusAtmos" -> modified.bonusAtmos
            "magicTrialProb" -> modified.magicTrialProb
            "magicSuccessProb" -> modified.magicSuccessProb
            "magicSuccessDamage" -> modified.magicSuccessDamage
            "dexMultiplier" -> modified.dexMultiplier
            "expMultiplier" -> modified.expMultiplier
            "injuryProb" -> modified.injuryProb
            "initWarPhase" -> modified.initWarPhase
            "sabotageDefence" -> modified.sabotageDefence
            "dedicationMultiplier" -> modified.dedicationMultiplier
            else -> value
        }
    }
}

/**
 * 도시치료: A general with medical skill heals injured generals in the same city each turn.
 * Legacy: OfficerTrigger/che_도시치료.php (priority 10010)
 *
 * - First heals the triggering general's own injury (if any).
 * - Then iterates city-mates with injury > 10, healing each with 50% probability.
 * - Nation=0 generals only heal other nation=0 generals; others heal anyone.
 */
class CityHealTrigger(
    private val general: Officer,
    private val cityMates: List<Officer>,
    private val rng: Random,
) : OfficerTrigger {
    override val uniqueId = "도시치료_${general.id}"
    override val priority = TriggerPriority.BEGIN + 10  // 10010

    override fun action(env: TriggerEnv): Boolean {
        // Heal self
        if (general.injury > 0) {
            general.injury = 0
        }

        // Heal city-mates with injury > 10, 50% chance each
        val patients = if (general.factionId == 0L) {
            cityMates.filter { it.id != general.id && it.factionId == 0L && it.injury > 10 }
        } else {
            cityMates.filter { it.id != general.id && it.injury > 10 }
        }

        for (patient in patients) {
            if (rng.nextDouble() < 0.5) {
                patient.injury = 0
            }
        }

        return true
    }
}

/**
 * 아이템치료: Medicine items auto-heal injury when injury >= threshold.
 * Legacy: OfficerTrigger/che_아이템치료.php
 *
 * Fires before command execution. If general has a medicine-type item
 * and injury >= injuryTarget, sets injury to 0.
 * Consumable medicine items decrement their remaining uses.
 */
class MedicineHealTrigger(
    private val general: Officer,
    private val injuryTarget: Int = 10,
) : OfficerTrigger {
    override val uniqueId = "아이템치료_${general.id}"
    override val priority = TriggerPriority.PRE  // Before injury reduction

    override fun action(env: TriggerEnv): Boolean {
        if (general.injury >= injuryTarget) {
            general.injury = 0
            env.vars["medicineHealed"] = true
            env.vars["medicineItemCode"] = general.accessoryCode
        }
        return true
    }
}

/**
 * Build the pre-turn trigger list for a general.
 * Legacy: TurnExecutionHelper::preprocessCommand()
 *
 * @param cityMates Other generals currently in the same city (used by CityHealTrigger).
 *                  Pass an empty list when city-mates are unavailable (e.g. realtime path).
 */
fun buildPreTurnTriggers(
    general: Officer,
    modifiers: List<ActionModifier> = emptyList(),
    cityMates: List<Officer> = emptyList(),
    rng: Random,
): List<OfficerTrigger> {
    val triggers = mutableListOf<OfficerTrigger>()

    // City heal trigger (before injury reduction so self-heal counts)
    if (cityMates.isNotEmpty()) {
        triggers.add(CityHealTrigger(general, cityMates, rng))
    }

    // Medicine item pre-turn heal (before injury reduction)
    val itemCode = general.accessoryCode
    if (itemCode != "None" && itemCode.isNotBlank()) {
        val itemTriggerTypes = ItemModifiers.getTriggerType(itemCode)
        if (itemTriggerTypes == "medicine") {
            // Default threshold is 10; some items may override via auxVar
            val threshold = (general.meta["use_treatment"] as? Number)?.toInt() ?: 10
            triggers.add(MedicineHealTrigger(general, threshold))
        }
    }

    // Always-present triggers
    triggers.add(InjuryReductionTrigger(general))
    if (modifiers.isNotEmpty()) {
        triggers.add(ModifierBridgeTrigger(general, modifiers))
    }

    return triggers
}

/**
 * Apply onCalcDomestic across a list of triggers.
 */
fun applyDomesticModifiers(
    triggers: List<OfficerTrigger>,
    general: Officer,
    turnType: String,
    varType: String,
    baseValue: Double,
    aux: Map<String, Any> = emptyMap(),
): Double {
    var value = baseValue
    for (trigger in triggers.sortedBy { it.priority }) {
        value = trigger.onCalcDomestic(general, turnType, varType, value, aux)
    }
    return value
}

/**
 * Apply onCalcStat across a list of triggers.
 */
fun applyStatModifiers(
    triggers: List<OfficerTrigger>,
    general: Officer,
    statName: String,
    baseValue: Double,
    aux: Map<String, Any> = emptyMap(),
): Double {
    var value = baseValue
    for (trigger in triggers.sortedBy { it.priority }) {
        value = trigger.onCalcStat(general, statName, value, aux)
    }
    return value
}
