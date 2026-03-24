package com.openlogh.engine.war

import com.openlogh.entity.Planet
import com.openlogh.model.ArmType
import com.openlogh.model.ShipClass
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class BattleEngine {
    companion object {
        const val ARM_PER_PHASE = 500.0
    }


    fun collectTriggers(unit: WarUnit): List<BattleTrigger> {
        if (unit !is WarUnitGeneral) return emptyList()
        val triggers = mutableListOf<BattleTrigger>()
        if (unit.officer.specialCode != "None") {
            BattleTriggerRegistry.get(unit.officer.specialCode)?.let { triggers.add(it) }
        }
        if (unit.officer.special2Code != "None") {
            BattleTriggerRegistry.get(unit.officer.special2Code)?.let { triggers.add(it) }
        }
        return triggers.sortedBy { it.priority }
    }

    fun resolveBattle(
        attacker: WarUnitGeneral,
        defenders: List<WarUnit>,
        city: Planet,
        rng: Random,
    ): BattleResult {
        var totalAttackerDamageDealt = 0
        var totalDefenderDamageDealt = 0
        var attackerWon = true

        // C7: track damage dealt for experience calculation
        var attackerDamageDealtForExp = 0
        var defenderDamageDealtForExp = 0

        val sortedDefenders = defenders.sortedByDescending { it.calcBattleOrder() }

        for (defender in sortedDefenders) {
            if (!attacker.continueWar().canContinue) {
                attackerWon = false
                break
            }

            attacker.training++
            defender.training++

            val attackerTriggers = collectTriggers(attacker)
            val defenderTriggers = collectTriggers(defender)

            var initCtx = BattleTriggerContext(attacker, defender, rng)
            for (t in attackerTriggers) initCtx = t.onBattleInit(initCtx)
            if (initCtx.injuryImmune) attacker.injuryImmune = true

            var phaseNumber = 0
            while (attacker.continueWar().canContinue && defender.continueWar().canContinue) {
                val (aDmg, dDmg) = runPhase(
                    attacker, defender, attackerTriggers, defenderTriggers, rng, phaseNumber, false,
                )
                totalAttackerDamageDealt += aDmg
                totalDefenderDamageDealt += dDmg

                // C7: accumulate exp-damage per phase
                attackerDamageDealtForExp += aDmg
                defenderDamageDealtForExp += dDmg

                // C7: grant level exp per phase (attacker full, defenders 0.8x)
                attacker.officer.experience += aDmg / 50
                if (defender is WarUnitGeneral) {
                    defender.officer.experience += (dDmg * 0.8 / 50).toInt()
                }

                attacker.morale -= 1
                defender.morale -= 3

                attacker.consumeRice(aDmg)

                phaseNumber++
                if (phaseNumber > 200) break
            }

            defender.applyResults()
        }

        if (!attacker.continueWar().canContinue) {
            attackerWon = false
        }

        // C8: Defender injury — after battle resolution, 5% chance per defender officer
        for (defender in sortedDefenders) {
            if (defender is WarUnitGeneral) {
                if (rng.nextDouble() < 0.05) {
                    val woundAmount = 10 + rng.nextInt(71) // random [10, 80]
                    defender.officer.injury = minOf(defender.officer.injury + woundAmount, 80).toShort()
                }
            }
        }

        var cityOccupied = false
        if (attackerWon) {
            val cityUnit = WarUnitCity(city)

            attacker.training++

            val attackerTriggers = collectTriggers(attacker)

            var initCtx = BattleTriggerContext(attacker, cityUnit, rng, isVsCity = true)
            for (t in attackerTriggers) initCtx = t.onBattleInit(initCtx)

            var phaseNumber = 0
            while (attacker.continueWar().canContinue && cityUnit.isAlive) {
                val (aDmg, dDmg) = runPhase(
                    attacker, cityUnit, attackerTriggers, emptyList(), rng, phaseNumber, true,
                )
                totalAttackerDamageDealt += aDmg
                totalDefenderDamageDealt += dDmg
                attackerDamageDealtForExp += aDmg

                // C7: phase exp during city assault
                attacker.officer.experience += aDmg / 50

                attacker.morale -= 1
                attacker.consumeRice(aDmg, vsCity = true)

                phaseNumber++
                if (phaseNumber > 200) break
            }

            if (!cityUnit.isAlive) {
                cityOccupied = true
                // C7: planet capture bonus exp
                attacker.officer.experience += 1000
            }

            cityUnit.applyResults()
        }

        // C7: Win/loss morale bonuses
        if (attackerWon) {
            attacker.morale = (attacker.morale * 1.1).toInt()
            for (defender in sortedDefenders) {
                defender.morale = (defender.morale * 1.05).toInt()
            }
        } else {
            for (defender in sortedDefenders) {
                defender.morale = (defender.morale * 1.1).toInt()
            }
            attacker.morale = (attacker.morale * 1.05).toInt()
        }

        // C7: Stat exp +1 routed by armType
        val atkArmType = resolveShipClass(attacker.officer.shipClass).armType
        when (atkArmType) {
            ArmType.FOOTMAN -> attacker.officer.commandExp = (attacker.officer.commandExp + 1).toShort()
            ArmType.ARCHER  -> attacker.officer.intelligenceExp = (attacker.officer.intelligenceExp + 1).toShort()
            ArmType.CAVALRY -> attacker.officer.mobilityExp = (attacker.officer.mobilityExp + 1).toShort()
            ArmType.WIZARD  -> attacker.officer.intelligenceExp = (attacker.officer.intelligenceExp + 1).toShort()
            ArmType.SIEGE   -> attacker.officer.attackExp = (attacker.officer.attackExp + 1).toShort()
            else            -> attacker.officer.commandExp = (attacker.officer.commandExp + 1).toShort()
        }

        attacker.applyResults()

        return BattleResult(
            attackerWon = attackerWon || cityOccupied,
            cityOccupied = cityOccupied,
            attackerDamageDealt = totalAttackerDamageDealt,
            defenderDamageDealt = totalDefenderDamageDealt,
        )
    }

    private fun getShipClass(unit: WarUnit): ShipClass {
        return if (unit is WarUnitGeneral) resolveShipClass(unit.officer.shipClass)
        else ShipClass.CASTLE
    }

    private fun runPhase(
        attacker: WarUnit,
        defender: WarUnit,
        attackerTriggers: List<BattleTrigger>,
        defenderTriggers: List<BattleTrigger>,
        rng: Random,
        phaseNumber: Int,
        isVsCity: Boolean,
    ): Pair<Int, Int> {
        var ctx = BattleTriggerContext(attacker, defender, rng, phaseNumber, isVsCity)

        for (t in attackerTriggers) ctx = t.onPreCritical(ctx)
        ctx.criticalActivated = rng.nextDouble() < (0.05 + ctx.criticalChanceBonus)

        for (t in attackerTriggers) ctx = t.onPostCritical(ctx)

        var dodged = false
        if (!ctx.dodgeDisabled) {
            var defCtx = BattleTriggerContext(defender, attacker, rng, phaseNumber, false)
            for (t in defenderTriggers) defCtx = t.onPreDodge(defCtx)
            dodged = rng.nextDouble() < (0.03 + defCtx.dodgeChanceBonus)
        }

        for (t in attackerTriggers) ctx = t.onPreMagic(ctx)
        if (ctx.magicChanceBonus > 0 && rng.nextDouble() < ctx.magicChanceBonus) {
            ctx.magicActivated = true
            for (t in attackerTriggers) ctx = t.onPostMagic(ctx)
            for (t in defenderTriggers) {
                val defMagicCtx = BattleTriggerContext(defender, attacker, rng, phaseNumber, false)
                defMagicCtx.magicActivated = true
                t.onPostMagic(defMagicCtx)
                if (defMagicCtx.magicReflected) ctx.magicReflected = true
            }
        } else if (ctx.magicChanceBonus > 0) {
            for (t in attackerTriggers) ctx = t.onMagicFail(ctx)
        }

        for (t in attackerTriggers) ctx = t.onDamageCalc(ctx)

        val atkShipClass = getShipClass(attacker)
        val defShipClass = getShipClass(defender)
        val atkArmType = atkShipClass.armType
        val defArmType = defShipClass.armType

        val attackCoef = atkShipClass.attackCoef[defArmType.code.toString()] ?: 1.0
        val defenceCoef = defShipClass.defenceCoef[atkArmType.code.toString()] ?: 1.0

        val atkDex = when (attacker) {
            is WarUnitGeneral -> attacker.getDexForArmType(defArmType)
            is WarUnitCity -> attacker.getDexForArmType(defArmType)
            else -> 0
        }
        val defDex = when (defender) {
            is WarUnitGeneral -> defender.getDexForArmType(atkArmType)
            is WarUnitCity -> defender.getDexForArmType(atkArmType)
            else -> 0
        }
        val dexFactor = getDexLog(atkDex, defDex)

        val critMult = if (ctx.criticalActivated) 1.5 else 1.0

        val rawAtk = attacker.getBaseAttack() * ctx.attackMultiplier * attackCoef * defenceCoef *
                dexFactor * critMult * (attacker.training / 100.0) * (attacker.morale / 100.0)
        val defBase = defender.getBaseDefence() * ctx.defenceMultiplier * (defender.training / 100.0)

        var attackerDamage = if (!dodged) max(1, (rawAtk - defBase * 0.3).toInt()) else 0

        if (ctx.magicActivated && !ctx.magicReflected) {
            attackerDamage += (attacker.getBaseAttack() * 0.3 * ctx.magicDamageMultiplier).toInt()
        }

        val revAttackCoef = defShipClass.attackCoef[atkArmType.code.toString()] ?: 1.0
        val revDefenceCoef = atkShipClass.defenceCoef[defArmType.code.toString()] ?: 1.0
        val revDexFactor = getDexLog(defDex, atkDex)

        val rawDef = defender.getBaseAttack() * revAttackCoef * revDefenceCoef *
                revDexFactor * (defender.training / 100.0) * (defender.morale / 100.0)
        val atkBase = attacker.getBaseDefence() * (attacker.training / 100.0)
        val defenderDamage = max(1, (rawDef - atkBase * 0.3).toInt())

        attacker.takeDamage(defenderDamage)
        defender.takeDamage(attackerDamage)

        for (t in attackerTriggers) ctx = t.onPostDamage(ctx)

        if (ctx.counterDamageRatio > 0) {
            val counterDmg = (defenderDamage * ctx.counterDamageRatio).toInt()
            defender.takeDamage(counterDmg)
        }

        if (ctx.moraleBoost > 0) {
            attacker.morale += ctx.moraleBoost
        }

        for (t in attackerTriggers) ctx = t.onInjuryCheck(ctx)
        if (!ctx.injuryImmune && !attacker.injuryImmune && attacker is WarUnitGeneral) {
            if (defenderDamage > 0 && rng.nextDouble() < 0.03) {
                attacker.injury += rng.nextInt(3) + 1
            }
        }

        if (ctx.snipeActivated && defender is WarUnitGeneral) {
            defender.injury += ctx.snipeWoundAmount
        }

        if (ctx.magicFailDamage > 0) {
            attacker.takeDamage(ctx.magicFailDamage.toInt())
        }

        return Pair(attackerDamage, defenderDamage)
    }
}
