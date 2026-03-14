package com.opensam.engine

import com.opensam.entity.City
import com.opensam.entity.General
import com.opensam.entity.Nation
import com.opensam.entity.WorldState
import com.opensam.model.ScenarioData
import com.opensam.service.ScenarioService
import com.opensam.test.InMemoryTurnHarness
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.time.OffsetDateTime

class DuelSimulationTest {

    @Test
    fun `duel world processes a tick and consumes queued player turn`() {
        val fx = setupWorld(userKing = true)

        fx.harness.turnService.processWorld(fx.world)

        assertTrue(fx.harness.generalTurnsFor(fx.dongKing.id).isEmpty())
        assertEquals(2, fx.world.currentMonth.toInt())
    }

    @Test
    fun `duel world keeps opposing city ownership without battle stub`() {
        val fx = setupWorld(userKing = true)

        fx.harness.turnService.processWorld(fx.world)

        assertEquals(2L, fx.city2.nationId)
    }

    @Test
    fun `multi turn duel invokes unification checks each tick`() {
        val fx = setupWorld(userKing = false)

        fx.harness.turnService.processWorld(fx.world)
        fx.world.updatedAt = OffsetDateTime.now().minusSeconds(90)
        fx.harness.turnService.processWorld(fx.world)

        verify(fx.harness.unificationService, times(2)).checkAndSettleUnification(fx.world)
    }

    @Test
    fun `mixed user and npc generals process in same tick without crashes`() {
        val fx = setupWorld(userKing = true, addWanderer = true)

        fx.harness.turnService.processWorld(fx.world)

        assertTrue(fx.harness.generalTurnsFor(fx.dongKing.id).isEmpty())
        assertEquals(2, fx.world.currentMonth.toInt())
    }

    private fun setupWorld(userKing: Boolean, addWanderer: Boolean = false): Fixture {
        val harness = InMemoryTurnHarness()
        val world = WorldState(
            id = 1,
            name = "duel-world",
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 1,
            tickSeconds = 60,
            updatedAt = OffsetDateTime.now().minusSeconds(90),
        )
        world.config = mutableMapOf("mapName" to "duel", "mapCode" to "duel", "hiddenSeed" to "test")

        val dong = Nation(id = 1, worldId = 1, name = "동국", color = "#ff3300", level = 3, strategicCmdLimit = 10)
        val seo = Nation(id = 2, worldId = 1, name = "서국", color = "#0033ff", level = 3, strategicCmdLimit = 10)
        val city1 = City(
            id = 1, worldId = 1, name = "동성", level = 5, nationId = 1,
            supplyState = 1, frontState = 1, pop = 10000, popMax = 50000,
            agri = 500, agriMax = 1000, comm = 500, commMax = 1000,
            secu = 500, secuMax = 1000, trust = 100, def = 500, defMax = 1000, wall = 500, wallMax = 1000,
        )
        val city2 = City(
            id = 2, worldId = 1, name = "서성", level = 5, nationId = 2,
            supplyState = 1, frontState = 1, pop = 10000, popMax = 50000,
            agri = 500, agriMax = 1000, comm = 500, commMax = 1000,
            secu = 500, secuMax = 1000, trust = 100, def = 500, defMax = 1000, wall = 500, wallMax = 1000,
        )

        val baseTurnTime = OffsetDateTime.now().minusSeconds(120)
        val dongKing = General(
            id = 11, worldId = 1, name = "동왕", nationId = 1, cityId = 1,
            officerLevel = 20, npcState = if (userKing) 0 else 2, crew = 2000, train = 100, atmos = 100,
            leadership = 95, strength = 90, intel = 80, turnTime = baseTurnTime,
        )
        val dongOfficer = General(
            id = 12, worldId = 1, name = "동장", nationId = 1, cityId = 1,
            officerLevel = 8, npcState = 2, crew = 1500, train = 100, atmos = 100,
            leadership = 82, strength = 78, intel = 75, turnTime = baseTurnTime.plusSeconds(1),
        )
        val seoKing = General(
            id = 21, worldId = 1, name = "서왕", nationId = 2, cityId = 2,
            officerLevel = 20, npcState = 2, crew = 1800, train = 100, atmos = 100,
            leadership = 92, strength = 88, intel = 84, turnTime = baseTurnTime.plusSeconds(2),
        )

        harness.putWorld(world)
        harness.putNation(dong)
        harness.putNation(seo)
        harness.putCity(city1)
        harness.putCity(city2)
        harness.putGeneral(dongKing)
        harness.putGeneral(dongOfficer)
        harness.putGeneral(seoKing)

        if (addWanderer) {
            harness.putGeneral(
                General(
                    id = 31,
                    worldId = 1,
                    name = "재야장수",
                    nationId = 0,
                    cityId = 1,
                    officerLevel = 0,
                    npcState = 1,
                    turnTime = baseTurnTime.plusSeconds(3),
                )
            )
        }

        val scenarioService = privateField<ScenarioService>(harness, "scenarioService")
        `when`(scenarioService.getScenario("test")).thenReturn(ScenarioData(startYear = 190))

        harness.queueGeneralTurn(generalId = 11, actionCode = "출병", arg = mutableMapOf("destCityId" to 2L))
        harness.queueNationTurn(nationId = 1, officerLevel = 20, actionCode = "Nation휴식")
        harness.queueNationTurn(nationId = 1, officerLevel = 8, actionCode = "Nation휴식")
        harness.queueNationTurn(nationId = 2, officerLevel = 20, actionCode = "Nation휴식")

        return Fixture(harness, world, city2, dongKing)
    }

    private fun <T> privateField(target: Any, fieldName: String): T {
        val field = target::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(target) as T
    }

    private data class Fixture(
        val harness: InMemoryTurnHarness,
        val world: WorldState,
        val city2: City,
        val dongKing: General,
    )
}
