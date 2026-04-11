package com.openlogh.model

/**
 * Detection system (색적) data model.
 *
 * Detection is automatic (no command needed).
 * SENSOR energy allocation affects range and precision.
 * Precision varies by distance and target unit type.
 * Detection evasion is hardware-based, improves when stopped (electronic warfare).
 * Detection info is shared among all friendly units in same battle.
 * If 2+ characters detect same enemy simultaneously = detection success.
 */
data class DetectionInfo(
    /** Detected unit's fleet ID */
    val targetFleetId: Long,
    /** Detected unit's faction ID */
    val targetFactionId: Long,
    /** Detection precision: 0.0 (unknown) to 1.0 (full detail) */
    val precision: Double,
    /** Whether the target position is known */
    val positionKnown: Boolean,
    /** Whether ship count is known */
    val shipCountKnown: Boolean,
    /** Whether unit composition is known */
    val compositionKnown: Boolean,
    /** Whether officer identity is known */
    val officerKnown: Boolean,
    /** Number of friendly units that simultaneously detected this target */
    val detectingUnitCount: Int = 0,
    /** Whether detection is confirmed (2+ simultaneous detectors) */
    val confirmed: Boolean = detectingUnitCount >= 2,
) {
    companion object {
        /** Precision thresholds for information reveal */
        const val THRESHOLD_POSITION = 0.2
        const val THRESHOLD_SHIP_COUNT = 0.4
        const val THRESHOLD_COMPOSITION = 0.6
        const val THRESHOLD_OFFICER = 0.8

        fun fromPrecision(
            targetFleetId: Long,
            targetFactionId: Long,
            precision: Double,
            detectingUnitCount: Int = 1,
        ): DetectionInfo = DetectionInfo(
            targetFleetId = targetFleetId,
            targetFactionId = targetFactionId,
            precision = precision,
            positionKnown = precision >= THRESHOLD_POSITION,
            shipCountKnown = precision >= THRESHOLD_SHIP_COUNT,
            compositionKnown = precision >= THRESHOLD_COMPOSITION,
            officerKnown = precision >= THRESHOLD_OFFICER,
            detectingUnitCount = detectingUnitCount,
        )
    }
}

/**
 * Per-unit detection capability parameters.
 */
data class DetectionCapability(
    /** Base detection range (grid units) */
    val baseRange: Double,
    /** Unit-specific precision parameter (0.0 to 1.0) */
    val basePrecision: Double,
    /** Detection evasion rating (0.0 to 1.0). Higher = harder to detect. */
    val evasionRating: Double,
    /** Whether unit is currently stopped (concentrating detection / electronic warfare) */
    val isStopped: Boolean = false,
) {
    /** Effective range after SENSOR energy multiplier */
    fun effectiveRange(sensorMultiplier: Double): Double =
        baseRange * sensorMultiplier * if (isStopped) 1.3 else 1.0

    /** Effective precision at given distance */
    fun effectivePrecision(distance: Double, sensorMultiplier: Double): Double {
        val range = effectiveRange(sensorMultiplier)
        if (distance > range) return 0.0
        val distanceFactor = 1.0 - (distance / range)
        val stoppedBonus = if (isStopped) 1.2 else 1.0
        return (basePrecision * distanceFactor * sensorMultiplier * stoppedBonus).coerceIn(0.0, 1.0)
    }

    /** Effective evasion rating. Improves when stopped (electronic warfare). */
    fun effectiveEvasion(): Double =
        if (isStopped) (evasionRating * 1.3).coerceAtMost(0.95) else evasionRating
}
