package com.openlogh.command.gin7.operations

import com.openlogh.command.CommandEnv
import com.openlogh.entity.Fleet
import com.openlogh.entity.Officer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Phase 12 Plan 12-02 Task 1: Regression test for the WarpNavigationCommand
 * Fleet.planetId bug. Without this fix, OPS-03 (arrival-triggered operation
 * activation) is broken for all cross-system operations because the Fleet
 * entity never receives the destination planet update.
 */
class WarpNavigationCommandTest {

    private fun mkEnv(): CommandEnv = CommandEnv(
        year = 800,
        month = 1,
        startYear = 790,
        sessionId = 1L,
    )

    @Test
    fun `updates_fleet_planet_id`() = runBlocking {
        val officer = Officer(name = "Reinhard").also {
            it.sessionId = 1L
            it.planetId = 10L
        }
        val fleet = Fleet(planetId = 10L).also {
            it.id = 1L
            it.sessionId = 1L
            it.factionId = 1L
        }

        val cmd = WarpNavigationCommand(officer, mkEnv(), mapOf("destPlanetId" to 42L))
        cmd.troop = fleet

        val result = cmd.run(Random(0))

        assertTrue(result.success)
        assertEquals(42L, officer.planetId)
        assertEquals(42L, fleet.planetId, "Fleet.planetId MUST be updated after warp — OPS-03 depends on this")
    }

    @Test
    fun `warp without fleet still updates officer`() = runBlocking {
        val officer = Officer(name = "Yang").also {
            it.sessionId = 1L
            it.planetId = 20L
        }

        val cmd = WarpNavigationCommand(officer, mkEnv(), mapOf("destPlanetId" to 99L))
        cmd.troop = null

        val result = cmd.run(Random(0))

        assertTrue(result.success)
        assertEquals(99L, officer.planetId)
    }
}
