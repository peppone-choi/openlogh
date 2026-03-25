package com.openlogh.command

import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.command.general.*
import com.openlogh.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import kotlin.random.Random

class CommandTest {

    private fun createTestGeneral(
        gold: Int = 1000,
        rice: Int = 1000,
        crew: Int = 0,
        crewType: Short = 0,
        train: Short = 0,
        atmos: Short = 0,
        leadership: Short = 50,
        strength: Short = 50,
        intel: Short = 50,
        politics: Short = 50,
        charm: Short = 50,
        nationId: Long = 1,
        cityId: Long = 1,
        officerLevel: Short = 0,
        troopId: Long = 0,
        experience: Int = 0,
        dedication: Int = 0,
        betray: Short = 0,
    ): General {
        return General(
            id = 1,
            worldId = 1,
            name = "테스트장수",
            nationId = nationId,
            cityId = cityId,
            gold = gold,
            rice = rice,
            crew = crew,
            crewType = crewType,
            train = train,
            atmos = atmos,
            leadership = leadership,
            strength = strength,
            intel = intel,
            politics = politics,
            charm = charm,
            officerLevel = officerLevel,
            troopId = troopId,
            experience = experience,
            dedication = dedication,
            betray = betray,
            turnTime = OffsetDateTime.now(),
        )
    }

    private fun createTestEnv(
        year: Int = 200,
        month: Int = 1,
        startYear: Int = 190,
    ) = CommandEnv(
        year = year,
        month = month,
        startYear = startYear,
        worldId = 1,
        realtimeMode = false,
    )

    private val fixedRng = Random(42)

    // ========== 휴식 (Rest) ==========

    @Test
    fun `휴식 command should succeed for any general`() {
        val general = createTestGeneral()
        val env = createTestEnv()
        val cmd = 휴식(general, env)
        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        assertTrue(result.logs.isNotEmpty())
        assertTrue(result.logs[0].contains("아무것도 실행하지 않았습니다"))
    }

    @Test
    fun `휴식 should have zero cost`() {
        val general = createTestGeneral()
        val env = createTestEnv()
        val cmd = 휴식(general, env)
        val cost = cmd.getCost()

        assertEquals(0, cost.funds)
        assertEquals(0, cost.supplies)
    }

    @Test
    fun `휴식 should have zero pre and post req turns`() {
        val general = createTestGeneral()
        val env = createTestEnv()
        val cmd = 휴식(general, env)

        assertEquals(0, cmd.getPreReqTurn())
        assertEquals(0, cmd.getPostReqTurn())
        assertEquals(0, cmd.getDuration())
    }

    @Test
    fun `휴식 should have no constraints`() {
        val general = createTestGeneral(nationId = 0)
        val env = createTestEnv()
        val cmd = 휴식(general, env)

        val fullResult = cmd.checkFullCondition()
        assertTrue(fullResult is ConstraintResult.Pass)
    }

    // ========== Date formatting ==========

    @Test
    fun `formatDate should pad month`() {
        val general = createTestGeneral()
        val env = createTestEnv(year = 200, month = 3)
        val cmd = 휴식(general, env)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.logs[0].contains("200년 03월"))
    }
}
