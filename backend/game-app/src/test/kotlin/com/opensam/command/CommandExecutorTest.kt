package com.opensam.command

import com.opensam.entity.City
import com.opensam.entity.General
import com.opensam.entity.Nation
import com.opensam.entity.WorldState
import com.opensam.test.InMemoryTurnHarness
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class CommandExecutorTest {

    @Test
    fun `executeGeneralCommand applies 요양 stat changes to general`() = runBlocking {
        val harness = InMemoryTurnHarness()
        val world = WorldState(
            id = 1,
            scenarioCode = "test",
            currentYear = 180,
            currentMonth = 1,
            tickSeconds = 300,
        )
        val nation = Nation(
            id = 1,
            worldId = 1,
            name = "테스트국가",
            color = "#FF0000",
            level = 1,
        )
        val city = City(
            id = 1,
            worldId = 1,
            name = "테스트도시",
            nationId = 1,
            supplyState = 1,
        )
        val general = General(
            id = 1,
            worldId = 1,
            name = "테스트장수",
            nationId = 1,
            cityId = 1,
            injury = 25,
            experience = 100,
            dedication = 50,
            turnTime = OffsetDateTime.now(),
        )
        val env = CommandEnv(
            year = 180,
            month = 1,
            startYear = 180,
            worldId = 1,
        )

        harness.putWorld(world)
        harness.putNation(nation)
        harness.putCity(city)
        harness.putGeneral(general)

        val result = harness.commandExecutor.executeGeneralCommand(
            actionCode = "요양",
            general = general,
            env = env,
            city = city,
            nation = nation,
        )

        assertTrue(result.success)
        assertEquals(0.toShort(), general.injury)
        assertEquals(110, general.experience)
        assertEquals(57, general.dedication)
    }
}
