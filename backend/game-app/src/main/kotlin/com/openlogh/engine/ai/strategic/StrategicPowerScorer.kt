package com.openlogh.engine.ai.strategic

import com.openlogh.entity.Fleet
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet

/**
 * Phase 13 SAI-02 — Strategic power evaluation for star systems.
 *
 * Pure object (no Spring DI) following the UtilityScorer pattern.
 * Computes a multi-dimensional composite power score for a star system from:
 *   - Fleet ship strength (currentUnits * 300 ships per gin7 standard)
 *   - Commander ability (average of command + leadership across stationed officers)
 *   - Defense infrastructure (orbital defense + fortress)
 *
 * Per D-01: 함선 수, 사령관 능력, 궤도방어/요새 방어력을 다차원 점수화.
 * Composite formula (raw additive — comparison-time normalization happens in
 * OperationTargetSelector when ranking candidates):
 *
 *   compositeScore = totalShips * 0.5 + commanderScore * 30.0 + defenseScore * 20.0
 *
 * The 30.0 / 20.0 multipliers compensate for the much smaller raw magnitudes of
 * commander and defense scores relative to fleet ship counts.
 */
object StrategicPowerScorer {

    /** Ships per fleet unit per gin7 manual / CLAUDE.md ship class table. */
    const val SHIPS_PER_UNIT: Int = 300

    /** Weight applied to total ships in compositeScore. */
    const val SHIPS_WEIGHT: Double = 0.5

    /** Weight applied to commander score in compositeScore. */
    const val COMMANDER_WEIGHT: Double = 30.0

    /** Weight applied to defense score in compositeScore. */
    const val DEFENSE_WEIGHT: Double = 20.0

    data class StarSystemPower(
        val totalShips: Int,
        val commanderScore: Double,
        val defenseScore: Double,
        val compositeScore: Double,
    )

    /**
     * Evaluate the composite strategic power of a star system.
     *
     * @param fleets fleets stationed at the planet (already filtered by caller)
     * @param officers officers stationed at the planet (already filtered by caller)
     * @param planet the planet whose orbital defense / fortress contribute to the score
     */
    fun evaluatePower(
        fleets: List<Fleet>,
        officers: List<Officer>,
        planet: Planet,
    ): StarSystemPower {
        val totalShips = fleets.sumOf { it.currentUnits * SHIPS_PER_UNIT }

        val commanderScore = if (officers.isEmpty()) {
            0.0
        } else {
            officers.sumOf { (it.command.toInt() + it.leadership.toInt()).toDouble() / 2.0 } /
                officers.size.toDouble()
        }

        val defenseScore = (planet.orbitalDefense + planet.fortress).toDouble()

        val compositeScore =
            totalShips * SHIPS_WEIGHT +
                commanderScore * COMMANDER_WEIGHT +
                defenseScore * DEFENSE_WEIGHT

        return StarSystemPower(
            totalShips = totalShips,
            commanderScore = commanderScore,
            defenseScore = defenseScore,
            compositeScore = compositeScore,
        )
    }
}
