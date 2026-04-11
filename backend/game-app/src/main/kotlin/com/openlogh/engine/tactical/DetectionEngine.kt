package com.openlogh.engine.tactical

import com.openlogh.model.DetectionCapability
import com.openlogh.model.DetectionInfo
import com.openlogh.model.EnergyAllocation
import kotlin.math.hypot

/**
 * Detection System (색적) engine for tactical combat.
 *
 * All flagships/fleet units/defense HQ have detection ability.
 * Detection is automatic (no command needed).
 * SENSOR energy allocation affects range and precision.
 * Precision varies by distance and target unit type.
 * Detection info shared among all friendly units in same battle.
 * If 2+ characters detect same enemy simultaneously = detection success.
 */
class DetectionEngine {

    /**
     * Perform detection sweep from a single unit against all enemy units.
     *
     * @param detector The detecting unit's capability
     * @param detectorPos Position of the detecting unit (x, y)
     * @param detectorEnergy Energy allocation of the detecting unit
     * @param enemyUnits List of enemy units with their positions and evasion
     * @return List of detection results for each enemy unit
     */
    fun detectFromUnit(
        detector: DetectionCapability,
        detectorPos: Pair<Double, Double>,
        detectorEnergy: EnergyAllocation,
        enemyUnits: List<DetectionTarget>,
    ): List<DetectionInfo> {
        val sensorMultiplier = detectorEnergy.sensorMultiplier()
        return enemyUnits.mapNotNull { target ->
            val distance = hypot(
                target.posX - detectorPos.first,
                target.posY - detectorPos.second,
            )
            val rawPrecision = detector.effectivePrecision(distance, sensorMultiplier)
            if (rawPrecision <= 0.0) return@mapNotNull null

            // Apply target's evasion
            val effectiveEvasion = target.evasion.effectiveEvasion()
            val finalPrecision = (rawPrecision * (1.0 - effectiveEvasion)).coerceIn(0.0, 1.0)
            if (finalPrecision <= 0.01) return@mapNotNull null

            DetectionInfo.fromPrecision(
                targetFleetId = target.fleetId,
                targetFactionId = target.factionId,
                precision = finalPrecision,
                detectingUnitCount = 1,
            )
        }
    }

    /**
     * Merge detection results from multiple friendly units.
     * Groups by target fleet ID, counts unique detectors,
     * and uses the best precision value.
     * 2+ simultaneous detectors = confirmed detection.
     */
    fun mergeDetections(allDetections: List<DetectionInfo>): List<DetectionInfo> {
        return allDetections
            .groupBy { it.targetFleetId }
            .map { (fleetId, detections) ->
                val bestPrecision = detections.maxOf { it.precision }
                val uniqueDetectors = detections.size
                DetectionInfo.fromPrecision(
                    targetFleetId = fleetId,
                    targetFactionId = detections.first().targetFactionId,
                    precision = bestPrecision,
                    detectingUnitCount = uniqueDetectors,
                )
            }
    }

    /**
     * Perform full detection sweep for one faction.
     * All friendly units detect, results are merged and shared.
     */
    fun performFactionDetection(
        friendlyUnits: List<DetectorUnit>,
        enemyUnits: List<DetectionTarget>,
    ): List<DetectionInfo> {
        val allDetections = mutableListOf<DetectionInfo>()
        for (unit in friendlyUnits) {
            val detections = detectFromUnit(
                detector = unit.capability,
                detectorPos = unit.posX to unit.posY,
                detectorEnergy = unit.energy,
                enemyUnits = enemyUnits,
            )
            allDetections.addAll(detections)
        }
        return mergeDetections(allDetections)
    }
}

/**
 * Input: a unit capable of detection.
 */
data class DetectorUnit(
    val fleetId: Long,
    val factionId: Long,
    val posX: Double,
    val posY: Double,
    val capability: DetectionCapability,
    val energy: EnergyAllocation,
)

/**
 * Input: an enemy unit that can be detected.
 */
data class DetectionTarget(
    val fleetId: Long,
    val factionId: Long,
    val posX: Double,
    val posY: Double,
    val evasion: DetectionCapability,
    val unitType: String = "FLEET",
)
