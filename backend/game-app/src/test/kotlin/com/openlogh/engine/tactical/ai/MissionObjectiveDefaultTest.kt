package com.openlogh.engine.tactical.ai

import com.openlogh.engine.ai.PersonalityTrait
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MissionObjectiveDefaultTest {
    @Test
    fun `aggressive personality defaults to SWEEP`() {
        assertEquals(MissionObjective.SWEEP, MissionObjective.defaultForPersonality(PersonalityTrait.AGGRESSIVE))
    }

    @Test
    fun `defensive personality defaults to DEFENSE`() {
        assertEquals(MissionObjective.DEFENSE, MissionObjective.defaultForPersonality(PersonalityTrait.DEFENSIVE))
    }

    @Test
    fun `cautious personality defaults to DEFENSE`() {
        assertEquals(MissionObjective.DEFENSE, MissionObjective.defaultForPersonality(PersonalityTrait.CAUTIOUS))
    }

    @Test
    fun `balanced personality defaults to DEFENSE`() {
        assertEquals(MissionObjective.DEFENSE, MissionObjective.defaultForPersonality(PersonalityTrait.BALANCED))
    }

    @Test
    fun `political personality defaults to DEFENSE`() {
        assertEquals(MissionObjective.DEFENSE, MissionObjective.defaultForPersonality(PersonalityTrait.POLITICAL))
    }
}
