package com.openlogh.engine.ai.strategic

import com.openlogh.entity.Officer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class FogOfWarEstimatorTest {

    private fun createOfficer(
        id: Long = 1,
        planetId: Long = 1,
        intelligence: Short = 50,
    ): Officer = Officer(
        id = id,
        sessionId = 1,
        name = "Spy$id",
        factionId = 1,
        planetId = planetId,
        intelligence = intelligence,
    )

    // ----- hasIntelligenceAgent -----

    @Test
    fun `officer with intelligence above threshold at target planet is an agent`() {
        val officer = createOfficer(planetId = 42, intelligence = 75)
        assertTrue(FogOfWarEstimator.hasIntelligenceAgent(42L, listOf(officer)))
    }

    @Test
    fun `officer at target planet but below intelligence threshold is not an agent`() {
        val officer = createOfficer(planetId = 42, intelligence = 60)
        assertFalse(FogOfWarEstimator.hasIntelligenceAgent(42L, listOf(officer)))
    }

    @Test
    fun `officer with high intelligence at different planet is not an agent`() {
        val officer = createOfficer(planetId = 99, intelligence = 80)
        assertFalse(FogOfWarEstimator.hasIntelligenceAgent(42L, listOf(officer)))
    }

    @Test
    fun `intelligence threshold constant is exactly 70`() {
        // Sanity-check the canonical D-02 threshold
        assertEquals(70, FogOfWarEstimator.INTELLIGENCE_THRESHOLD)
        // intelligence = 70 (exact threshold) qualifies
        val officer = createOfficer(planetId = 42, intelligence = 70)
        assertTrue(FogOfWarEstimator.hasIntelligenceAgent(42L, listOf(officer)))
    }

    // ----- applyFogNoise -----

    @Test
    fun `applyFogNoise with agent returns true power unchanged`() {
        val rng = Random(42)
        assertEquals(1000.0, FogOfWarEstimator.applyFogNoise(1000.0, hasAgent = true, rng = rng))
        assertEquals(0.0, FogOfWarEstimator.applyFogNoise(0.0, hasAgent = true, rng = rng))
    }

    @Test
    fun `applyFogNoise without agent stays within plus minus 40 percent over many trials`() {
        val truePower = 1000.0
        val rng = Random(42)
        repeat(100) {
            val estimated = FogOfWarEstimator.applyFogNoise(truePower, hasAgent = false, rng = rng)
            // ±40% noise window
            assertTrue(
                estimated in (truePower * 0.6)..(truePower * 1.4),
                "Estimated $estimated was outside [600, 1400]",
            )
        }
    }

    @Test
    fun `applyFogNoise never returns negative even when noise pushes below zero`() {
        val rng = Random(42)
        repeat(200) {
            val estimated = FogOfWarEstimator.applyFogNoise(0.5, hasAgent = false, rng = rng)
            assertTrue(estimated >= 0.0, "Estimated $estimated was negative")
        }
    }
}
