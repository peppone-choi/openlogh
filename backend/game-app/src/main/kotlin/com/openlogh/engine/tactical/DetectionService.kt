package com.openlogh.engine.tactical

import com.openlogh.model.DetectionCapability

/**
 * Wraps DetectionEngine for use within TacticalBattleState.
 * Builds detectionMatrix per faction per tick.
 *
 * Based on gin7 manual Chapter 4 (索敵):
 * - SENSOR energy allocation × base range determines detection sphere.
 * - Stationary units (STATIONED/ANCHORING) get +20% range bonus.
 * - Detection confirmed when precision >= 0.5 OR 2+ friendly units detect same target.
 * - Result written to TacticalBattleState.detectionMatrix (officerId → detected fleetId set).
 */
class DetectionService(
    private val detectionEngine: DetectionEngine = DetectionEngine(),
) {

    companion object {
        /** Base detection range in battle grid units (before SENSOR multiplier) */
        const val BASE_DETECTION_RANGE = 200.0

        /** Stationary stance bonus multiplier applied to detection range */
        const val STATIONARY_DETECTION_BONUS = 0.2

        /** Minimum precision threshold for a confirmed single-unit detection */
        const val PRECISION_THRESHOLD = 0.5

        /** Number of simultaneous detectors required to confirm detection regardless of precision */
        const val MIN_DETECTOR_COUNT = 2
    }

    /**
     * Rebuild the full detection matrix for all factions in [state].
     * Clears the existing matrix and writes fresh results.
     * Called every tick from TacticalBattleEngine.processTick().
     */
    fun updateDetectionMatrix(state: TacticalBattleState) {
        val aliveUnits = state.units.filter { it.isAlive }
        state.detectionMatrix.clear()

        for (side in listOf(BattleSide.ATTACKER, BattleSide.DEFENDER)) {
            val friendlies = aliveUnits.filter { it.side == side }
            val enemies = aliveUnits.filter { it.side != side }
            if (friendlies.isEmpty() || enemies.isEmpty()) continue

            val detectors = friendlies.map { u ->
                // gin7 rule: stationary stance → +20% detection range bonus
                val stationaryBonus = if (!u.stance.canMove) STATIONARY_DETECTION_BONUS else 0.0
                val effectiveRange = BASE_DETECTION_RANGE * u.energy.sensorMultiplier() * (1.0 + stationaryBonus)

                DetectorUnit(
                    fleetId = u.fleetId,
                    factionId = u.factionId,
                    posX = u.posX,
                    posY = u.posY,
                    capability = DetectionCapability(
                        baseRange = effectiveRange,
                        basePrecision = u.intelligence / 100.0,
                        evasionRating = 0.0,
                        isStopped = !u.stance.canMove,
                    ),
                    energy = u.energy,
                )
            }

            val targets = enemies.map { u ->
                DetectionTarget(
                    fleetId = u.fleetId,
                    factionId = u.factionId,
                    posX = u.posX,
                    posY = u.posY,
                    evasion = DetectionCapability(
                        baseRange = 0.0,
                        basePrecision = 0.0,
                        // High SENSOR allocation → better electronic warfare evasion
                        evasionRating = u.energy.sensorMultiplier() * 0.3,
                        isStopped = !u.stance.canMove,
                    ),
                    unitType = u.unitType,
                )
            }

            val results = detectionEngine.performFactionDetection(detectors, targets)

            // Confirmed detection: precision >= threshold OR 2+ simultaneous detectors
            val confirmedTargetFleetIds = results
                .filter { it.precision >= PRECISION_THRESHOLD || it.detectingUnitCount >= MIN_DETECTOR_COUNT }
                .map { it.targetFleetId }
                .toSet()

            // Write to detectionMatrix: all friendlies share the same detection info
            for (friendly in friendlies) {
                if (confirmedTargetFleetIds.isNotEmpty()) {
                    state.detectionMatrix
                        .getOrPut(friendly.officerId) { mutableSetOf() }
                        .addAll(confirmedTargetFleetIds)
                }
            }
        }
    }
}
