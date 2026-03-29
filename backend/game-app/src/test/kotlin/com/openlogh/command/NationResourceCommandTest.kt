package com.openlogh.command

import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.command.nation.Nation휴식
import com.openlogh.command.nation.che_감축
import com.openlogh.command.nation.che_백성동원
import com.openlogh.engine.DiplomacyService
import com.openlogh.entity.*
import com.openlogh.repository.CityRepository
import com.openlogh.repository.GeneralRepository
import com.openlogh.repository.NationRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.time.OffsetDateTime
import kotlin.random.Random

class NationResourceCommandTest {

    private val fixedRng = Random(42)

    private fun createGeneral(
        id: Long = 1,
        nationId: Long = 1,
        cityId: Long = 1,
        officerLevel: Short = 20,
        gold: Int = 1000,
        rice: Int = 1000,
    ): General = General(
        id = id,
        worldId = 1,
        name = "테스트장수$id",
        nationId = nationId,
        cityId = cityId,
        officerLevel = officerLevel,
        gold = gold,
        rice = rice,
        leadership = 50,
        strength = 50,
        intel = 50,
        politics = 50,
        charm = 50,
        turnTime = OffsetDateTime.now(),
    )

    private fun createCity(
        id: Long = 1,
        nationId: Long = 1,
        supplyState: Short = 1,
    ): City = City(
        id = id,
        worldId = 1,
        name = "테스트도시$id",
        nationId = nationId,
        supplyState = supplyState,
        agri = 500,
        agriMax = 1000,
        comm = 500,
        commMax = 1000,
        secu = 500,
        secuMax = 1000,
        def = 500,
        defMax = 1000,
        wall = 500,
        wallMax = 1000,
        pop = 10000,
        popMax = 50000,
        trust = 80,
    )

    private fun createNation(
        id: Long = 1,
        gold: Int = 200000,
        rice: Int = 200000,
        strategicCmdLimit: Short = 0,
    ): Nation = Nation(
        id = id,
        worldId = 1,
        name = "테스트국가$id",
        color = "#FF0000",
        gold = gold,
        rice = rice,
        level = 7,
        strategicCmdLimit = strategicCmdLimit,
        chiefGeneralId = 1,
    )

    private fun env(
        year: Int = 200,
        month: Int = 1,
        startYear: Int = 190,
    ): CommandEnv = CommandEnv(
        year = year,
        month = month,
        startYear = startYear,
        worldId = 1,
        realtimeMode = false,
    )

    @Test
    fun `Nation휴식 always passes condition and runs`() {
        val cmd = Nation휴식(createGeneral(officerLevel = 1), env())

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.logs.isEmpty())
    }

    // TODO: 몰수 test pending che_몰수 class implementation

    @Test
    fun `감축 fails for non-chief and runs with level down and capacity shrink`() {
        val nonChief = createGeneral(officerLevel = 5)
        val failCmd = che_감축(nonChief, env())
        failCmd.city = createCity(nationId = 1)
        failCmd.nation = createNation(id = 1)
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)

        val chief = createGeneral(officerLevel = 20)
        val nation = createNation(id = 1, gold = 10000, rice = 10000)
        val city = createCity(nationId = 1)
        city.level = 2
        city.popMax = 50000
        val cmd = che_감축(chief, env())
        cmd.city = city
        cmd.nation = nation

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(1, city.level.toInt())
        assertEquals(40000, city.popMax)
        assertEquals(9500, nation.gold)
        assertEquals(9500, nation.rice)
    }

    // TODO: 증축, 발령, 천도 tests pending class implementation

    @Test
    fun `백성동원 fails when strategic command is blocked and runs with NPC saves`() {
        val chief = createGeneral(officerLevel = 20)
        val failCmd = che_백성동원(chief, env())
        failCmd.city = createCity(nationId = 1)
        failCmd.destCity = createCity(id = 2, nationId = 1)
        failCmd.nation = createNation(id = 1, strategicCmdLimit = 3)
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)

        val generalRepository = mock(GeneralRepository::class.java)
        val mockServices = CommandServices(
            generalRepository = generalRepository,
            cityRepository = mock(CityRepository::class.java),
            nationRepository = mock(NationRepository::class.java),
            diplomacyService = mock(DiplomacyService::class.java),
        )

        val nation = createNation(id = 1, strategicCmdLimit = 0)
        val destCity = createCity(id = 2, nationId = 1)
        destCity.pop = 25000

        val cmd = che_백성동원(chief, env())
        cmd.city = createCity(nationId = 1)
        cmd.destCity = destCity
        cmd.nation = nation
        cmd.services = mockServices

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(9, nation.strategicCmdLimit.toInt())
        assertEquals(5000, destCity.pop)
        verify(generalRepository, times(2)).save(org.mockito.Mockito.any(General::class.java))
        assertTrue(result.logs.any { it.contains("백성") })
    }

    // TODO: Tests for che_물자원조, che_국기변경, che_국호변경 pending class implementation
}
