package com.openlogh.engine.war

import com.openlogh.model.DetectionCapability
import com.openlogh.model.DetectionInfo
import com.openlogh.model.EnergyAllocation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DetectionEngineTest {

    private lateinit var engine: DetectionEngine

    @BeforeEach
    fun setUp() {
        engine = DetectionEngine()
    }

    @Test
    fun `unit within range is detected`() {
        val detector = DetectionCapability(baseRange = 10.0, basePrecision = 0.8, evasionRating = 0.0)
        val target = DetectionTarget(
            fleetId = 2, factionId = 2, posX = 3.0, posY = 4.0,
            evasion = DetectionCapability(baseRange = 5.0, basePrecision = 0.5, evasionRating = 0.0),
        )
        val results = engine.detectFromUnit(
            detector = detector,
            detectorPos = 0.0 to 0.0,
            detectorEnergy = EnergyAllocation.BALANCED,
            enemyUnits = listOf(target),
        )
        assertEquals(1, results.size)
        assertTrue(results[0].precision > 0.0)
    }

    @Test
    fun `unit out of range is not detected`() {
        val detector = DetectionCapability(baseRange = 2.0, basePrecision = 0.8, evasionRating = 0.0)
        val target = DetectionTarget(
            fleetId = 2, factionId = 2, posX = 100.0, posY = 100.0,
            evasion = DetectionCapability(baseRange = 5.0, basePrecision = 0.5, evasionRating = 0.0),
        )
        val results = engine.detectFromUnit(
            detector = detector,
            detectorPos = 0.0 to 0.0,
            detectorEnergy = EnergyAllocation.BALANCED,
            enemyUnits = listOf(target),
        )
        assertTrue(results.isEmpty())
    }

    @Test
    fun `high evasion reduces detection precision`() {
        val detector = DetectionCapability(baseRange = 10.0, basePrecision = 0.8, evasionRating = 0.0)
        val lowEvasionTarget = DetectionTarget(
            fleetId = 2, factionId = 2, posX = 3.0, posY = 0.0,
            evasion = DetectionCapability(baseRange = 5.0, basePrecision = 0.5, evasionRating = 0.1),
        )
        val highEvasionTarget = DetectionTarget(
            fleetId = 3, factionId = 2, posX = 3.0, posY = 0.0,
            evasion = DetectionCapability(baseRange = 5.0, basePrecision = 0.5, evasionRating = 0.8),
        )
        val energy = EnergyAllocation.BALANCED
        val lowResult = engine.detectFromUnit(detector, 0.0 to 0.0, energy, listOf(lowEvasionTarget))
        val highResult = engine.detectFromUnit(detector, 0.0 to 0.0, energy, listOf(highEvasionTarget))

        assertTrue(lowResult.isNotEmpty())
        // High evasion should reduce precision
        if (highResult.isNotEmpty()) {
            assertTrue(lowResult[0].precision > highResult[0].precision)
        }
    }

    @Test
    fun `merge detections confirms when 2 or more detectors`() {
        val detections = listOf(
            DetectionInfo.fromPrecision(targetFleetId = 1, targetFactionId = 2, precision = 0.5, detectingUnitCount = 1),
            DetectionInfo.fromPrecision(targetFleetId = 1, targetFactionId = 2, precision = 0.7, detectingUnitCount = 1),
        )
        val merged = engine.mergeDetections(detections)
        assertEquals(1, merged.size)
        assertTrue(merged[0].confirmed) // 2 detectors
        assertEquals(0.7, merged[0].precision, 0.001) // best precision
        assertEquals(2, merged[0].detectingUnitCount)
    }

    @Test
    fun `single detector not confirmed`() {
        val detections = listOf(
            DetectionInfo.fromPrecision(targetFleetId = 1, targetFactionId = 2, precision = 0.9, detectingUnitCount = 1),
        )
        val merged = engine.mergeDetections(detections)
        assertEquals(1, merged.size)
        assertFalse(merged[0].confirmed)
    }

    @Test
    fun `stopped unit has better detection range`() {
        val moving = DetectionCapability(baseRange = 10.0, basePrecision = 0.5, evasionRating = 0.0, isStopped = false)
        val stopped = DetectionCapability(baseRange = 10.0, basePrecision = 0.5, evasionRating = 0.0, isStopped = true)
        assertTrue(stopped.effectiveRange(1.0) > moving.effectiveRange(1.0))
    }

    @Test
    fun `sensor energy allocation affects detection range`() {
        val cap = DetectionCapability(baseRange = 10.0, basePrecision = 0.5, evasionRating = 0.0)
        val lowSensor = EnergyAllocation(beam = 25, gun = 25, shield = 25, engine = 20, warp = 5, sensor = 0)
        val highSensor = EnergyAllocation(beam = 10, gun = 10, shield = 20, engine = 15, warp = 5, sensor = 40)
        assertTrue(highSensor.sensorMultiplier() > lowSensor.sensorMultiplier())
    }
}
