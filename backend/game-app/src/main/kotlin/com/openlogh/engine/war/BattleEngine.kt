package com.openlogh.engine.war

import com.openlogh.entity.Planet
import com.openlogh.model.ShipClass
import kotlin.math.max
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

                attacker.morale -= 1
                attacker.consumeRice(aDmg, vsCity = true)

                phaseNumber++
                if (phaseNumber > 200) break
            }

            if (!cityUnit.isAlive) {
                cityOccupied = true
            }

            cityUnit.applyResults()
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
