package com.openlogh.engine.tactical.ai

import com.openlogh.engine.ai.PersonalityTrait
import com.openlogh.model.Formation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for tactical AI data model: MissionObjective, TacticalPersonalityConfig, TacticalAIContext.
 */
class TacticalAIDataModelTest {

    // ── MissionObjective ──

    @Test
    fun `MissionObjective has CONQUEST, DEFENSE, SWEEP values`() {
        assertEquals("점령", MissionObjective.CONQUEST.korean)
        assertEquals("방어", MissionObjective.DEFENSE.korean)
        assertEquals("소탕", MissionObjective.SWEEP.korean)
        assertEquals(3, MissionObjective.entries.size)
    }

    // ── TacticalPersonalityConfig ──

    @Test
    fun `AGGRESSIVE profile has retreatHpThreshold 0_10`() {
        val profile = TacticalPersonalityConfig.forTrait(PersonalityTrait.AGGRESSIVE)
        assertEquals(0.10, profile.retreatHpThreshold)
    }

    @Test
    fun `CAUTIOUS profile has retreatHpThreshold 0_30`() {
        val profile = TacticalPersonalityConfig.forTrait(PersonalityTrait.CAUTIOUS)
        assertEquals(0.30, profile.retreatHpThreshold)
    }

    @Test
    fun `DEFENSIVE profile has retreatHpThreshold 0_20`() {
        val profile = TacticalPersonalityConfig.forTrait(PersonalityTrait.DEFENSIVE)
        assertEquals(0.20, profile.retreatHpThreshold)
    }

    @Test
    fun `BALANCED profile has retreatHpThreshold 0_20`() {
        val profile = TacticalPersonalityConfig.forTrait(PersonalityTrait.BALANCED)
        assertEquals(0.20, profile.retreatHpThreshold)
    }

    @Test
    fun `POLITICAL profile has retreatHpThreshold 0_20`() {
        val profile = TacticalPersonalityConfig.forTrait(PersonalityTrait.POLITICAL)
        assertEquals(0.20, profile.retreatHpThreshold)
    }

    @Test
    fun `each personality has distinct preferredFormation`() {
        assertEquals(Formation.WEDGE, TacticalPersonalityConfig.forTrait(PersonalityTrait.AGGRESSIVE).preferredFormation)
        assertEquals(Formation.MIXED, TacticalPersonalityConfig.forTrait(PersonalityTrait.DEFENSIVE).preferredFormation)
        assertEquals(Formation.BY_CLASS, TacticalPersonalityConfig.forTrait(PersonalityTrait.CAUTIOUS).preferredFormation)
        assertEquals(Formation.MIXED, TacticalPersonalityConfig.forTrait(PersonalityTrait.BALANCED).preferredFormation)
        assertEquals(Formation.THREE_COLUMN, TacticalPersonalityConfig.forTrait(PersonalityTrait.POLITICAL).preferredFormation)
    }

    @Test
    fun `AGGRESSIVE has highest aggressionFactor`() {
        val aggressive = TacticalPersonalityConfig.forTrait(PersonalityTrait.AGGRESSIVE)
        val cautious = TacticalPersonalityConfig.forTrait(PersonalityTrait.CAUTIOUS)
        assertTrue(aggressive.aggressionFactor > cautious.aggressionFactor)
        assertEquals(0.9, aggressive.aggressionFactor)
        assertEquals(0.2, cautious.aggressionFactor)
    }

    @Test
    fun `all 5 personalities return valid profiles`() {
        for (trait in PersonalityTrait.entries) {
            val profile = TacticalPersonalityConfig.forTrait(trait)
            assertTrue(profile.retreatHpThreshold in 0.0..1.0, "retreatHpThreshold out of range for $trait")
            assertTrue(profile.retreatMoraleThreshold in 0..100, "retreatMoraleThreshold out of range for $trait")
            assertTrue(profile.preferredEngagementRange > 0.0, "preferredEngagementRange must be positive for $trait")
            assertTrue(profile.aggressionFactor in 0.0..1.0, "aggressionFactor out of range for $trait")
        }
    }

    // ── TacticalAIContext ──

    @Test
    fun `TacticalAIContext is a data class with all required fields`() {
        // Verify it compiles and all fields are accessible
        val ctx = TacticalAIContext::class
        val fields = ctx.java.declaredFields.map { it.name }.toSet()
        assertTrue("unit" in fields)
        assertTrue("allies" in fields)
        assertTrue("enemies" in fields)
        assertTrue("mission" in fields)
        assertTrue("personality" in fields)
        assertTrue("profile" in fields)
        assertTrue("currentTick" in fields)
        assertTrue("anchorX" in fields)
        assertTrue("anchorY" in fields)
        assertTrue("battleBoundsX" in fields)
        assertTrue("battleBoundsY" in fields)
    }
}
