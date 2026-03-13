package com.opensam.command

import com.opensam.entity.City
import com.opensam.entity.General
import com.opensam.entity.Nation
import com.opensam.entity.WorldState
import com.opensam.test.InMemoryTurnHarness
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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

    @Test
    fun `executeGeneralCommand creates wandering nation on 거병 for unaffiliated general`() = runBlocking {
        val harness = InMemoryTurnHarness()
        val world = WorldState(
            id = 1,
            scenarioCode = "test",
            currentYear = 180,
            currentMonth = 2,
            tickSeconds = 300,
            config = mutableMapOf("startyear" to 180),
        )
        val city = City(
            id = 1,
            worldId = 1,
            name = "테스트도시",
            level = 5,
            nationId = 0,
            supplyState = 1,
        )
        val general = General(
            id = 1,
            worldId = 1,
            name = "테스트장수",
            nationId = 0,
            cityId = 1,
            officerLevel = 0,
            turnTime = OffsetDateTime.now(),
        )
        val env = CommandEnv(
            year = 180,
            month = 2,
            startYear = 180,
            worldId = 1,
        )

        harness.putWorld(world)
        harness.putCity(city)
        harness.putGeneral(general)

        val result = harness.commandExecutor.executeGeneralCommand(
            actionCode = "거병",
            general = general,
            env = env,
            city = city,
            nation = null,
        )

        assertTrue(result.success)
        assertNotEquals(0L, general.nationId)
        assertEquals(12.toShort(), general.officerLevel)

        val nations = harness.nationRepository.findByWorldId(1)
        assertEquals(1, nations.size)
        assertEquals(general.nationId, nations.first().id)
        assertEquals(0.toShort(), nations.first().level)
    }

    @Test
    fun `executeGeneralCommand founds nation directly from unaffiliated general`() = runBlocking {
        val harness = InMemoryTurnHarness()
        val world = WorldState(
            id = 1,
            scenarioCode = "test",
            currentYear = 180,
            currentMonth = 2,
            tickSeconds = 300,
            config = mutableMapOf("startyear" to 180),
        )
        val city = City(
            id = 1,
            worldId = 1,
            name = "테스트도시",
            level = 5,
            nationId = 0,
            supplyState = 1,
        )
        val general = General(
            id = 1,
            worldId = 1,
            name = "테스트장수",
            nationId = 0,
            cityId = 1,
            officerLevel = 0,
            turnTime = OffsetDateTime.now(),
        )
        val env = CommandEnv(
            year = 180,
            month = 2,
            startYear = 180,
            worldId = 1,
        )

        harness.putWorld(world)
        harness.putCity(city)
        harness.putGeneral(general)

        val result = harness.commandExecutor.executeGeneralCommand(
            actionCode = "건국",
            general = general,
            env = env,
            arg = mapOf(
                "nationName" to "신국",
                "nationType" to "군벌",
                "colorType" to 1,
            ),
            city = city,
            nation = null,
        )

        assertTrue(result.success)
        assertNotEquals(0L, general.nationId)
        assertEquals(12.toShort(), general.officerLevel)
        assertEquals(general.nationId, city.nationId)

        val foundedNation = harness.nationRepository.findById(general.nationId).orElse(null)
        assertNotNull(foundedNation)
        assertEquals("신국", foundedNation!!.name)
        assertEquals(1.toShort(), foundedNation.level)
        assertEquals("che_군벌", foundedNation.typeCode)
    }
}
