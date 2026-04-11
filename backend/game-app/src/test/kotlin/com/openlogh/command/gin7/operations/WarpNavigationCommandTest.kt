package com.openlogh.command.gin7.operations

import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandServices
import com.openlogh.engine.DiplomacyService
import com.openlogh.entity.Fleet
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.Optional
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

    // ── Phase 24-27 (gap E38): 항행불능 그리드 차단 ──

    private fun makeServices(planetRepo: PlanetRepository): CommandServices =
        CommandServices(
            officerRepository = mock(OfficerRepository::class.java),
            planetRepository = planetRepo,
            factionRepository = mock(FactionRepository::class.java),
            diplomacyService = mock(DiplomacyService::class.java),
        )

    private fun stubPlanet(id: Long, name: String, navigableMeta: Any? = null): Planet {
        val planet = Planet(sessionId = 1L, name = name)
        planet.id = id
        if (navigableMeta != null) {
            planet.meta["navigable"] = navigableMeta
        }
        return planet
    }

    @Test
    fun `E38 - navigable meta false blocks warp`() = runBlocking {
        val officer = Officer(name = "Mittermeyer").also {
            it.sessionId = 1L
            it.planetId = 10L
        }
        val fleet = Fleet(planetId = 10L).also {
            it.id = 1L
            it.sessionId = 1L
            it.factionId = 1L
        }
        val cmd = WarpNavigationCommand(officer, mkEnv(), mapOf("destPlanetId" to 777L))
        cmd.troop = fleet

        val planetRepo = mock(PlanetRepository::class.java)
        `when`(planetRepo.findById(777L))
            .thenReturn(Optional.of(stubPlanet(777L, "사르가소 성운", navigableMeta = false)))
        cmd.services = makeServices(planetRepo)

        val result = cmd.run(Random(0))

        assertFalse(result.success, "warp must fail when destination grid is non-navigable")
        assertTrue(result.message?.contains("항행불능 그리드") == true,
            "fail reason must mention the navigation block. got: ${result.message}")
        assertEquals(10L, officer.planetId, "officer stayed at source planet")
        assertEquals(10L, fleet.planetId, "fleet stayed at source planet")
    }

    @Test
    fun `E38 - navigable meta true allows warp`() = runBlocking {
        val officer = Officer(name = "Bittenfeld").also {
            it.sessionId = 1L
            it.planetId = 10L
        }
        val cmd = WarpNavigationCommand(officer, mkEnv(), mapOf("destPlanetId" to 55L))
        cmd.troop = null

        val planetRepo = mock(PlanetRepository::class.java)
        `when`(planetRepo.findById(55L))
            .thenReturn(Optional.of(stubPlanet(55L, "오딘", navigableMeta = true)))
        cmd.services = makeServices(planetRepo)

        val result = cmd.run(Random(0))
        assertTrue(result.success)
        assertEquals(55L, officer.planetId)
    }

    @Test
    fun `E38 - missing navigable meta defaults to navigable`() = runBlocking {
        // Legacy planets (no meta["navigable"] key) must keep working.
        val officer = Officer(name = "Kircheis").also {
            it.sessionId = 1L
            it.planetId = 10L
        }
        val cmd = WarpNavigationCommand(officer, mkEnv(), mapOf("destPlanetId" to 88L))

        val planetRepo = mock(PlanetRepository::class.java)
        `when`(planetRepo.findById(88L))
            .thenReturn(Optional.of(stubPlanet(88L, "하이네센", navigableMeta = null)))
        cmd.services = makeServices(planetRepo)

        val result = cmd.run(Random(0))
        assertTrue(result.success, "absence of navigable flag must not break existing scenarios")
    }

    @Test
    fun `E38 - navigable accepts string false as blocker`() = runBlocking {
        // JSONB round-trip may deserialize booleans as strings; the guard must tolerate both.
        val officer = Officer(name = "Schenkopp").also {
            it.sessionId = 1L
            it.planetId = 10L
        }
        val cmd = WarpNavigationCommand(officer, mkEnv(), mapOf("destPlanetId" to 999L))

        val planetRepo = mock(PlanetRepository::class.java)
        `when`(planetRepo.findById(999L))
            .thenReturn(Optional.of(stubPlanet(999L, "소행성대", navigableMeta = "false")))
        cmd.services = makeServices(planetRepo)

        val result = cmd.run(Random(0))
        assertFalse(result.success, "string-typed false must also block entry")
    }
}
