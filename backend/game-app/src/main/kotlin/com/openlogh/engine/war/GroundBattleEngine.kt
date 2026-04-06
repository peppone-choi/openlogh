package com.openlogh.engine.war

import com.openlogh.model.GroundUnitType
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Ground battle box state for a single faction in a ground combat.
 */
data class GroundBattleUnit(
    val factionId: Long,
    val unitType: GroundUnitType,
    var unitCount: Int,
    var hp: Int,
    val maxHp: Int,
    val officerId: Long? = null,
    val officerName: String? = null,
) {
    val isAlive: Boolean get() = hp > 0 && unitCount > 0
}

/**
 * Result of a ground battle resolution.
 */
data class GroundBattleResult(
    val attackerWon: Boolean,
    val attackerRemainingUnits: Int,
    val defenderRemainingUnits: Int,
    val logs: List<String>,
    /** If attacker won, capture is complete (all enemy ground units eliminated) */
    val captureComplete: Boolean,
)

/**
 * Ground Combat System (지상전) engine.
 *
 * When ground troops are deployed to a planet/fortress surface, ground battle occurs.
 * - Ground battle box: max 30 units per faction
 * - Ground units in battle box count toward grid's 300 unit limit
 * - Defense garrison deploys in ground battle box, awaits enemy ground forces
 * - Defense coverage = garrison units / 30 (displayed as percentage on planet)
 */
class GroundBattleEngine {

    companion object {
        /** Maximum units per faction in ground battle box */
        const val MAX_GROUND_UNITS = 30

        /** Grid unit limit that includes ground units */
        const val GRID_UNIT_LIMIT = 300

        /** Base damage per combat round */
        const val BASE_GROUND_DAMAGE = 15.0

        /** Maximum number of ground combat rounds before stalemate */
        const val MAX_ROUNDS = 50
    }

    /**
     * Calculate defense coverage percentage for a planet.
     * @param garrisonUnits Number of garrison units deployed
     * @return Coverage as fraction 0.0 to 1.0
     */
    fun calculateDefenseCoverage(garrisonUnits: Int): Double =
        (garrisonUnits.toDouble() / MAX_GROUND_UNITS).coerceIn(0.0, 1.0)

    /**
     * Validate ground unit types for the given terrain.
     */
    fun validateUnitTypes(
        units: List<GroundUnitType>,
        isGasPlanet: Boolean,
        isFortress: Boolean,
    ): List<GroundUnitType> {
        val allowed = GroundUnitType.allowedFor(isGasPlanet, isFortress)
        return units.filter { it in allowed }
    }

    /**
     * Resolve a ground battle between attacking and defending ground forces.
     */
    fun resolveGroundBattle(
        attackers: List<GroundBattleUnit>,
        defenders: List<GroundBattleUnit>,
        rng: Random,
    ): GroundBattleResult {
        val logs = mutableListOf<String>()
        val activeAttackers = attackers.filter { it.isAlive }.toMutableList()
        val activeDefenders = defenders.filter { it.isAlive }.toMutableList()

        if (activeAttackers.isEmpty()) {
            logs.add("공격측 지상부대 없음. 지상전 불발.")
            return GroundBattleResult(
                attackerWon = false,
                attackerRemainingUnits = 0,
                defenderRemainingUnits = activeDefenders.sumOf { it.unitCount },
                logs = logs,
                captureComplete = false,
            )
        }

        if (activeDefenders.isEmpty()) {
            logs.add("방어측 지상부대 없음. 무혈 점령.")
            return GroundBattleResult(
                attackerWon = true,
                attackerRemainingUnits = activeAttackers.sumOf { it.unitCount },
                defenderRemainingUnits = 0,
                logs = logs,
                captureComplete = true,
            )
        }

        logs.add("지상전 개시: 공격 ${activeAttackers.sumOf { it.unitCount }}유닛 vs 방어 ${activeDefenders.sumOf { it.unitCount }}유닛")

        var round = 0
        while (round < MAX_ROUNDS && activeAttackers.any { it.isAlive } && activeDefenders.any { it.isAlive }) {
            round++

            // Each attacker unit attacks a random defender
            for (attacker in activeAttackers.filter { it.isAlive }) {
                val target = activeDefenders.filter { it.isAlive }.randomOrNull() ?: break
                val damage = calculateGroundDamage(attacker, target, rng)
                target.hp -= damage
                if (target.hp <= 0) {
                    target.hp = 0
                    target.unitCount = 0
                    logs.add("  [R$round] ${attacker.unitType.displayNameKo} -> ${target.unitType.displayNameKo} 격파")
                }
            }

            // Each surviving defender attacks a random attacker
            for (defender in activeDefenders.filter { it.isAlive }) {
                val target = activeAttackers.filter { it.isAlive }.randomOrNull() ?: break
                val damage = calculateGroundDamage(defender, target, rng)
                target.hp -= damage
                if (target.hp <= 0) {
                    target.hp = 0
                    target.unitCount = 0
                    logs.add("  [R$round] ${defender.unitType.displayNameKo} -> ${target.unitType.displayNameKo} 격파")
                }
            }

            // Remove dead units
            activeAttackers.removeAll { !it.isAlive }
            activeDefenders.removeAll { !it.isAlive }
        }

        val attackerWon = activeDefenders.isEmpty() && activeAttackers.isNotEmpty()
        val captureComplete = activeDefenders.isEmpty()

        if (attackerWon) {
            logs.add("지상전 종료: 공격측 승리. 잔여 ${activeAttackers.sumOf { it.unitCount }}유닛.")
        } else if (activeAttackers.isEmpty()) {
            logs.add("지상전 종료: 방어측 승리. 잔여 ${activeDefenders.sumOf { it.unitCount }}유닛.")
        } else {
            logs.add("지상전 종료: 교착 상태 (${MAX_ROUNDS}라운드 소진).")
        }

        return GroundBattleResult(
            attackerWon = attackerWon,
            attackerRemainingUnits = activeAttackers.sumOf { it.unitCount },
            defenderRemainingUnits = activeDefenders.sumOf { it.unitCount },
            logs = logs,
            captureComplete = captureComplete,
        )
    }

    private fun calculateGroundDamage(
        attacker: GroundBattleUnit,
        defender: GroundBattleUnit,
        rng: Random,
    ): Int {
        val atkPower = attacker.unitType.attack * attacker.unitCount
        val defPower = defender.unitType.defense * defender.unitCount
        val ratio = atkPower.toDouble() / defPower.toDouble().coerceAtLeast(1.0)
        val variance = 0.8 + rng.nextDouble() * 0.4  // 0.8 to 1.2
        return (BASE_GROUND_DAMAGE * ratio * variance).roundToInt().coerceAtLeast(1)
    }
}
