package com.openlogh.engine.tactical

import com.openlogh.model.TacticalWeaponEvent
import com.openlogh.model.TacticalWeaponType
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Missile and Fighter weapon system for tactical combat.
 *
 * Based on gin7 manual Chapter 4:
 * - MISSILE: long-range, consumes missileCount per shot, uniform damage within range.
 * - FIGHTER (Spartanian): launched from carriers, low direct damage,
 *   applies 30% speed debuff for 60 ticks. FIGHTER vs FIGHTER = 2x damage (intercept).
 */
class MissileWeaponSystem {

    /**
     * Process a missile attack from source to target.
     * Returns null if missileCount=0, out of range, or miss.
     * On success: decrements source.missileCount by 1 and returns TacticalWeaponEvent.
     */
    fun processMissileAttack(
        source: TacticalUnit,
        target: TacticalUnit,
        state: TacticalBattleState,
        rng: Random = Random,
    ): TacticalWeaponEvent? {
        if (source.missileCount <= 0) return null

        val dist = distance(source, target)
        val missileRange = TacticalWeaponType.MISSILE.baseRange * 100.0  // 8.0 * 100 = 800 units
        if (dist > missileRange) return null

        // Consume missile
        source.missileCount--

        // Hit chance: SENSOR-based 80~95%
        val hitChance = (0.80 + source.energy.sensorMultiplier() * 0.15).coerceAtMost(0.95)
        if (rng.nextDouble() >= hitChance) {
            state.tickEvents.add(
                BattleTickEvent("missile_miss", source.fleetId, target.fleetId, 0, "미사일 빗나감")
            )
            return null
        }

        val rawDmg = TacticalWeaponType.MISSILE.baseDamage
        val attackMod = source.formation.attackModifier * source.stance.attackModifier * (source.attack / 50.0)
        val defenseMod = target.formation.defenseModifier * target.stance.defenseModifier * target.energy.shieldAbsorption()
        val finalDmg = ((rawDmg * attackMod) * (1.0 - defenseMod)).toInt().coerceAtLeast(1)

        target.hp -= finalDmg
        val shipLoss = (finalDmg.toDouble() / target.maxHp.coerceAtLeast(1) * target.maxShips).toInt()
        target.ships = (target.ships - shipLoss).coerceAtLeast(0)

        state.tickEvents.add(
            BattleTickEvent(
                "missile_hit", source.fleetId, target.fleetId, finalDmg,
                "미사일 ${source.officerName}→${target.officerName} ($finalDmg 피해)"
            )
        )

        return TacticalWeaponEvent(
            weaponType = TacticalWeaponType.MISSILE,
            sourceFleetId = source.fleetId,
            targetFleetId = target.fleetId,
            damage = finalDmg,
            supplyCost = TacticalWeaponType.MISSILE.supplyCostPerUse,
            tick = state.tickCount,
        )
    }

    /**
     * Process a fighter (Spartanian) launch from source to target.
     * Only CARRIER unit types may launch fighters.
     * On hit: applies fighterSpeedDebuffTicks = 60 to target.
     * FIGHTER vs CARRIER: damage is doubled (intercept).
     * Returns null if not a carrier, out of range, or miss.
     */
    fun processFighterAttack(
        source: TacticalUnit,
        target: TacticalUnit,
        state: TacticalBattleState,
        rng: Random = Random,
    ): TacticalWeaponEvent? {
        // Only carriers can launch fighters
        if (!source.unitType.contains("CARRIER", ignoreCase = true)) return null

        val dist = distance(source, target)
        val fighterRange = TacticalWeaponType.FIGHTER.baseRange * 100.0  // 6.0 * 100 = 600 units
        if (dist > fighterRange) return null

        val hitChance = (0.65 + source.energy.sensorMultiplier() * 0.2).coerceAtMost(0.90)
        if (rng.nextDouble() >= hitChance) return null

        val isFighterVsFighter = target.unitType.contains("CARRIER", ignoreCase = true)
        val rawDmg = TacticalWeaponType.FIGHTER.baseDamage *
            if (isFighterVsFighter) TacticalWeaponType.FIGHTER_INTERCEPT_DAMAGE_MULTIPLIER.toInt() else 1

        target.hp -= rawDmg
        // Apply speed debuff for 60 ticks
        target.fighterSpeedDebuffTicks = TacticalWeaponType.FIGHTER_DEBUFF_DURATION_TICKS

        state.tickEvents.add(
            BattleTickEvent(
                "fighter_attack", source.fleetId, target.fleetId, rawDmg,
                "전투정 발진 → 속도저하${if (isFighterVsFighter) " (인터셉트 2배)" else ""}"
            )
        )

        return TacticalWeaponEvent(
            weaponType = TacticalWeaponType.FIGHTER,
            sourceFleetId = source.fleetId,
            targetFleetId = target.fleetId,
            damage = rawDmg,
            supplyCost = TacticalWeaponType.FIGHTER.supplyCostPerUse,
            tick = state.tickCount,
            isIntercept = isFighterVsFighter,
            speedReduction = TacticalWeaponType.FIGHTER_SPEED_REDUCTION,
        )
    }

    private fun distance(a: TacticalUnit, b: TacticalUnit): Double =
        sqrt((a.posX - b.posX) * (a.posX - b.posX) + (a.posY - b.posY) * (a.posY - b.posY))
}
