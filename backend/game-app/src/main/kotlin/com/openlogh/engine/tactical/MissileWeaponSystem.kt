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
     * 스파르타니안(FIGHTER) 출격 처리.
     * 출격은 CARRIER 유닛만 가능하며, 피격 시 target.fighterSpeedDebuffTicks = 60 이
     * 적용되어 60 틱 동안 이동 속도가 감소한다. 대상이 또 다른 CARRIER 이면 요격전으로
     * 판정되어 데미지가 2 배가 된다.
     * 반환값: 비 CARRIER, 사정 거리 초과, 군수물자 부족, 빗나감 시 null.
     *
     * Phase 24-18 (gap A5/C2, gin7 매뉴얼 p49):
     *   "전투정 발진 — 공격력은 낮으나 상대의 이동 속도를 저하시킨다. 공모함만 운용 가능.
     *    출격에는 일률 10 군수물자가 소모된다. 자함을 공격하는 전투정도 공격 대상이
     *    될 수 있다 (요격전)."
     *
     * 구현 노트:
     *   - 10 군수물자는 source.supplies 에서 출격 시점에 즉시 차감된다 — 비용은 출격당
     *     한 번이며 명중 여부와 무관하다. 빗나가도 물자는 사라진다.
     *   - supplies < 10 이면 출격 자체가 gating 되어 null 이 반환된다.
     *   - FIGHTER vs CARRIER 는 요격전으로 자동 판정 — 데미지 2 배. 매뉴얼의
     *     "자함을 공격하는 전투정도 공격 대상" 규칙을 구현한다.
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

        // Phase 24-18 gin7 p49: 전투정(Spartanian) sortie requires 10 군수물자.
        // Gate the launch before the RNG hit roll so that an impossible sortie
        // never fires at all.
        val sortieCost = TacticalWeaponType.FIGHTER.supplyCostPerUse
        if (source.supplies < sortieCost) {
            state.tickEvents.add(
                BattleTickEvent(
                    "fighter_sortie_aborted", source.fleetId, target.fleetId, 0,
                    "스파르타니안 출격 실패 — 군수물자 부족 (필요 $sortieCost / 보유 ${source.supplies})"
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
                    "스파르타니안 발진 실패 — 요격당함 / 빗나감 (군수물자 -$sortieCost)"
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
                "스파르타니안 발진 → 속도저하${if (isFighterVsFighter) " (요격전 2배)" else " (대함전)"} (군수물자 -$sortieCost)"
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
