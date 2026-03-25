package com.openlogh.command

import com.openlogh.entity.*
import com.openlogh.test.InMemoryTurnHarness
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class CommandExecutorTest {

    @Test
    fun `executeGeneralCommand runs 휴식 successfully`() = runBlocking {
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
            actionCode = "휴식",
            general = general,
            env = env,
            city = city,
            nation = nation,
        )

        assertTrue(result.success)
    }

    @Test
    fun `cooldown failure log includes command name with color tag`() = runBlocking {
        val harness = InMemoryTurnHarness()
        val world = WorldState(
            id = 1, scenarioCode = "test", currentYear = 180, currentMonth = 1, tickSeconds = 300,
            config = mutableMapOf("startyear" to 180),
        )
        val nation = Nation(id = 1, worldId = 1, name = "테스트국가", level = 1)
        val city = City(id = 1, worldId = 1, name = "도시", nationId = 1, supplyState = 1)
        val general = General(
            id = 1, worldId = 1, name = "장수", nationId = 1, cityId = 1,
            turnTime = OffsetDateTime.now(),
        ).apply {
            meta["next_execute"] = mutableMapOf<String, Any>("휴식" to (180 * 12 + 5))
        }
        val env = CommandEnv(year = 180, month = 1, startYear = 180, worldId = 1)

        harness.putWorld(world)
        harness.putNation(nation)
        harness.putCity(city)
        harness.putGeneral(general)

        val result = harness.commandExecutor.executeGeneralCommand(
            actionCode = "휴식", general = general, env = env, city = city, nation = nation,
        )

        assertTrue(!result.success, "Command should fail due to cooldown")
        assertTrue(result.logs.isNotEmpty())
        val log = result.logs.first()
        assertTrue(log.contains("<R>"), "Cooldown log should contain <R> tag: $log")
        assertTrue(log.contains("쿨다운"), "Cooldown log should mention cooldown: $log")
    }
}
