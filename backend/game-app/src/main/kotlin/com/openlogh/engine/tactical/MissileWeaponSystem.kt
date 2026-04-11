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
     * Returns null if not a carrier, out of range, insufficient supplies, or miss.
     *
     * Phase 24-18 (gap A5/C2, gin7 manual p49):
     *   "戦闘艇の発進 — 攻撃力は弱いが相手の移動速度を低下させる。空母のみ運用可能。
     *    出撃には一律 10 軍需物資が必要。自艦を攻撃する戦闘艇も攻撃対象になる (迎撃)"
     *
     * Implementation notes:
     *   - 10 軍需物資 is deducted from `source.supplies` up front — the cost is per
     *     SORTIE (出撃), not per hit. A missed launch still consumes supplies.
     *   - If supplies < 10, the launch is gated entirely (no partial sortie).
     *   - FIGHTER vs CARRIER is 迎撃戦 (intercept): damage is doubled per the
     *     manual's "自艦を攻撃する戦闘艇も攻撃対象" rule.
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

        // Phase 24-18 gin7 p49: 戦闘艇 sortie requires 10 軍需物資. Gate the launch
        // before the RNG hit roll so that an impossible sortie never fires at all.
        val sortieCost = TacticalWeaponType.FIGHTER.supplyCostPerUse
        if (source.supplies < sortieCost) {
            state.tickEvents.add(
                BattleTickEvent(
                    "fighter_sortie_aborted", source.fleetId, target.fleetId, 0,
                    "스파르타니안 출격 실패 — 軍需物資 부족 (필요 $sortieCost / 보유 ${source.supplies})"
                )
            )
            return null
        }

        // Cost is paid at sortie time, not at hit time (manual rule).
        source.supplies -= sortieCost

        val hitChance = (0.65 + source.energy.sensorMultiplier() * 0.2).coerceAtMost(0.90)
        if (rng.nextDouble() >= hitChance) {
            state.tickEvents.add(
                BattleTickEvent(
                    "fighter_miss", source.fleetId, target.fleetId, 0,
                    "스파르타니안 발진 실패 — 요격당함 / 빗나감 (軍需物資 -$sortieCost)"
                )
            )
            return null
        }

        val isFighterVsFighter = target.unitType.contains("CARRIER", ignoreCase = true)
        val rawDmg = TacticalWeaponType.FIGHTER.baseDamage *
            if (isFighterVsFighter) TacticalWeaponType.FIGHTER_INTERCEPT_DAMAGE_MULTIPLIER.toInt() else 1

        target.hp -= rawDmg
        // Apply speed debuff for 60 ticks
        target.fighterSpeedDebuffTicks = TacticalWeaponType.FIGHTER_DEBUFF_DURATION_TICKS

        state.tickEvents.add(
            BattleTickEvent(
                "fighter_attack", source.fleetId, target.fleetId, rawDmg,
                "스파르타니안 발진 → 속도저하${if (isFighterVsFighter) " (迎撃戦 2배)" else " (対艦戦)"} (軍需物資 -$sortieCost)"
            )
        )

        return TacticalWeaponEvent(
            weaponType = TacticalWeaponType.FIGHTER,
            sourceFleetId = source.fleetId,
            targetFleetId = target.fleetId,
            damage = rawDmg,
            supplyCost = sortieCost,
            tick = state.tickCount,
            isIntercept = isFighterVsFighter,
            speedReduction = TacticalWeaponType.FIGHTER_SPEED_REDUCTION,
        )
    }

    private fun distance(a: TacticalUnit, b: TacticalUnit): Double =
        sqrt((a.posX - b.posX) * (a.posX - b.posX) + (a.posY - b.posY) * (a.posY - b.posY))
}
