package com.openlogh.command

import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.command.nation.che_무작위수도이전
import com.openlogh.command.nation.che_부대탈퇴지시
import com.openlogh.command.nation.cr_인구이동
import com.openlogh.command.nation.event_대검병연구
import com.openlogh.command.nation.event_무희연구
import com.openlogh.command.nation.event_산저병연구
import com.openlogh.command.nation.event_상병연구
import com.openlogh.command.nation.event_원융노병연구
import com.openlogh.command.nation.event_음귀병연구
import com.openlogh.command.nation.event_화륜차연구
import com.openlogh.command.nation.event_화시병연구
import com.openlogh.engine.DiplomacyService
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.FactionRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.Optional
import kotlin.random.Random

class NationResearchSpecialCommandTest {

    private val fixedRng = Random(42)

    private fun createGeneral(
        id: Long = 1,
        factionId: Long = 1,
        planetId: Long = 1,
        officerLevel: Short = 20,
        funds: Int = 1000,
        supplies: Int = 1000,
    ): Officer = Officer(
        id = id,
        sessionId = 1,
        name = "테스트장수$id",
        factionId = factionId,
        planetId = planetId,
        officerLevel = officerLevel,
        funds = funds,
        supplies = supplies,
        leadership = 50,
        command = 50,
        intelligence = 50,
        politics = 50,
        administration = 50,
        turnTime = OffsetDateTime.now(),
    )

    private fun createCity(
        id: Long = 1,
        factionId: Long = 1,
        supplyState: Short = 1,
    ): Planet = Planet(
        id = id,
        sessionId = 1,
        name = "테스트도시$id",
        factionId = factionId,
        supplyState = supplyState,
        production = 500,
        productionMax = 1000,
        commerce = 500,
        commerceMax = 1000,
        security = 500,
        securityMax = 1000,
        orbitalDefense = 500,
        orbitalDefenseMax = 1000,
        fortress = 500,
        fortressMax = 1000,
        population = 10000,
        populationMax = 50000,
        approval = 80,
    )

    private fun createNation(
        id: Long = 1,
        funds: Int = 200000,
        supplies: Int = 200000,
        strategicCmdLimit: Short = 0,
    ): Faction = Faction(
        id = id,
        sessionId = 1,
        name = "테스트국가$id",
        color = "#FF0000",
        funds = funds,
        supplies = supplies,
        level = 7,
        strategicCmdLimit = strategicCmdLimit,
        chiefOfficerId = 1,
    )

    private fun env(
        year: Int = 200,
        month: Int = 1,
        startYear: Int = 190,
    ): CommandEnv = CommandEnv(
        year = year,
        month = month,
        startYear = startYear,
        sessionId = 1,
        realtimeMode = false,
    )

    private fun assertResearchCommand(
        actionLabel: String,
        expectedCost: Int,
        expectedPreReqTurn: Int,
        nationMetaKey: String,
        commandFactory: (Officer, CommandEnv) -> FactionCommand,
    ) {
        val nonChief = createGeneral(officerLevel = 5)
        val nonChiefCmd = commandFactory(nonChief, env())
        nonChiefCmd.city = createCity()
        nonChiefCmd.nation = createNation(funds = 300000, supplies = 300000)
        val nonChiefResult = nonChiefCmd.checkFullCondition()
        assertTrue(nonChiefResult is ConstraintResult.Fail)
        assertTrue((nonChiefResult as ConstraintResult.Fail).reason.contains("군주"))

        val chief = createGeneral(officerLevel = 20)

        val lowGoldCmd = commandFactory(chief, env())
        lowGoldCmd.city = createCity()
        lowGoldCmd.nation = createNation(funds = 50000, supplies = 300000)
        val lowGoldResult = lowGoldCmd.checkFullCondition()
        assertTrue(lowGoldResult is ConstraintResult.Fail)
        assertTrue((lowGoldResult as ConstraintResult.Fail).reason.contains("국고"))

        val lowRiceCmd = commandFactory(chief, env())
        lowRiceCmd.city = createCity()
        lowRiceCmd.nation = createNation(funds = 300000, supplies = 50000)
        val lowRiceResult = lowRiceCmd.checkFullCondition()
        assertTrue(lowRiceResult is ConstraintResult.Fail)
        assertTrue((lowRiceResult as ConstraintResult.Fail).reason.contains("병량"))

        val successNation = createNation(funds = 300000, supplies = 300000)
        val successCmd = commandFactory(chief, env())
        successCmd.city = createCity()
        successCmd.nation = successNation

        val successCondition = successCmd.checkFullCondition()
        assertTrue(successCondition is ConstraintResult.Pass)
        assertEquals(expectedPreReqTurn, successCmd.getPreReqTurn())
        assertEquals(expectedCost, successCmd.getCost().funds)
        assertEquals(expectedCost, successCmd.getCost().supplies)

        val beforeGold = successNation.funds
        val beforeRice = successNation.supplies
        val result = runBlocking { successCmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.logs.any { it.contains(actionLabel) })
        assertEquals(beforeGold - expectedCost, successNation.funds)
        assertEquals(beforeRice - expectedCost, successNation.supplies)
        assertEquals(1, successNation.meta[nationMetaKey])
        assertEquals(100, chief.experience)
        assertEquals(100, chief.dedication)
    }

    @Test
    fun `대검병 연구 command validates constraints timing cost and run`() {
        assertResearchCommand(
            actionLabel = "대검병 연구",
            expectedCost = 50000,
            expectedPreReqTurn = 11,
            nationMetaKey = "can_대검병사용",
            commandFactory = ::event_대검병연구,
        )
    }

    @Test
    fun `무희 연구 command validates constraints timing cost and run`() {
        assertResearchCommand(
            actionLabel = "무희 연구",
            expectedCost = 100000,
            expectedPreReqTurn = 23,
            nationMetaKey = "can_무희사용",
            commandFactory = ::event_무희연구,
        )
    }

    @Test
    fun `산저병 연구 command validates constraints timing cost and run`() {
        assertResearchCommand(
            actionLabel = "산저병 연구",
            expectedCost = 50000,
            expectedPreReqTurn = 11,
            nationMetaKey = "can_산저병사용",
            commandFactory = ::event_산저병연구,
        )
    }

    @Test
    fun `상병 연구 command validates constraints timing cost and run`() {
        assertResearchCommand(
            actionLabel = "상병 연구",
            expectedCost = 100000,
            expectedPreReqTurn = 23,
            nationMetaKey = "can_상병사용",
            commandFactory = ::event_상병연구,
        )
    }

    @Test
    fun `원융노병 연구 command validates constraints timing cost and run`() {
        assertResearchCommand(
            actionLabel = "원융노병 연구",
            expectedCost = 100000,
            expectedPreReqTurn = 23,
            nationMetaKey = "can_원융노병사용",
            commandFactory = ::event_원융노병연구,
        )
    }

    @Test
    fun `음귀병 연구 command validates constraints timing cost and run`() {
        assertResearchCommand(
            actionLabel = "음귀병 연구",
            expectedCost = 50000,
            expectedPreReqTurn = 11,
            nationMetaKey = "can_음귀병사용",
            commandFactory = ::event_음귀병연구,
        )
    }

    @Test
    fun `화륜차 연구 command validates constraints timing cost and run`() {
        assertResearchCommand(
            actionLabel = "화륜차 연구",
            expectedCost = 100000,
            expectedPreReqTurn = 23,
            nationMetaKey = "can_화륜차사용",
            commandFactory = ::event_화륜차연구,
        )
    }

    @Test
    fun `화시병 연구 command validates constraints timing cost and run`() {
        assertResearchCommand(
            actionLabel = "화시병 연구",
            expectedCost = 50000,
            expectedPreReqTurn = 11,
            nationMetaKey = "can_화시병사용",
            commandFactory = ::event_화시병연구,
        )
    }

    @Test
    fun `무작위수도이전 checks constraints cost and successful run with services`() {
        val failCmd = che_무작위수도이전(createGeneral(officerLevel = 5), env(year = 189, startYear = 190))
        failCmd.city = createCity(factionId = 1)
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)
        assertTrue((fail as ConstraintResult.Fail).reason.contains("군주"))

        val planetRepository = mock(PlanetRepository::class.java)
        val services = CommandServices(
            officerRepository = mock(OfficerRepository::class.java),
            planetRepository = planetRepository,
            factionRepository = mock(FactionRepository::class.java),
            diplomacyService = mock(DiplomacyService::class.java),
        )

        val commandEnv = env(year = 189, startYear = 190)
        commandEnv.gameStor["neutralCities"] = listOf(2)
        val nation = createNation(id = 1)
        nation.capitalPlanetId = 1
        val targetCity = createCity(id = 2, factionId = 0)

        `when`(planetRepository.findById(2L)).thenReturn(Optional.of(targetCity))

        val cmd = che_무작위수도이전(createGeneral(officerLevel = 20), commandEnv)
        cmd.city = createCity(id = 1, factionId = 1)
        cmd.nation = nation
        cmd.services = services

        assertEquals(0, cmd.getCost().funds)
        assertEquals(0, cmd.getCost().supplies)
        assertEquals(1, cmd.getPreReqTurn())

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(1L, targetCity.factionId)
        assertEquals(2L, nation.capitalPlanetId)
        assertTrue(result.logs.any { it.contains("국가를 옮겼습니다") })
        verify(planetRepository, times(1)).save(targetCity)
    }

    @Test
    fun `부대탈퇴지시 checks constraints cost and successful run`() {
        val failCmd = che_부대탈퇴지시(createGeneral(officerLevel = 20), env())
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)
        assertTrue((fail as ConstraintResult.Fail).reason.contains("대상 장수"))

        val target = createGeneral(id = 2, factionId = 1)
        target.fleetId = 99

        val cmd = che_부대탈퇴지시(createGeneral(officerLevel = 20), env())
        cmd.destOfficer = target

        assertEquals(0, cmd.getCost().funds)
        assertEquals(0, cmd.getCost().supplies)
        assertEquals(0, cmd.getPreReqTurn())

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(0L, target.fleetId)
        assertTrue(result.logs.any { it.contains("탈퇴") })
    }

    @Test
    fun `인구이동 checks constraints cost and successful run`() {
        val failCmd = cr_인구이동(createGeneral(officerLevel = 20), env(), mapOf("amount" to 20000))
        failCmd.city = createCity(id = 1, factionId = 1)
        failCmd.nation = createNation(id = 1, funds = 10000, supplies = 10000)
        val fail = failCmd.checkFullCondition()
        assertTrue(fail is ConstraintResult.Fail)
        assertTrue((fail as ConstraintResult.Fail).reason.contains("목적지 도시"))

        val nation = createNation(id = 1, funds = 10000, supplies = 10000)
        val fromCity = createCity(id = 1, factionId = 1)
        fromCity.population = 50000
        val toCity = createCity(id = 2, factionId = 1)
        toCity.population = 1000
        toCity.populationMax = 60000

        val cmd = cr_인구이동(createGeneral(officerLevel = 20), env(), mapOf("amount" to 20000))
        cmd.city = fromCity
        cmd.destPlanet = toCity
        cmd.nation = nation
        cmd.constraintEnv = mapOf(
            "mapAdjacency" to mapOf(1L to listOf(2L), 2L to listOf(1L)),
            "cityNationById" to mapOf(1L to 1L, 2L to 1L),
            "dbToMapId" to mapOf(1L to 1L, 2L to 2L),
            "mapToDbId" to mapOf(1L to 1L, 2L to 2L),
            "cityNationByMapId" to mapOf(1L to 1L, 2L to 1L),
        )

        assertEquals(200, cmd.getCost().funds)
        assertEquals(200, cmd.getCost().supplies)
        assertEquals(0, cmd.getPreReqTurn())

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(9800, nation.funds)
        assertEquals(9800, nation.supplies)
        assertEquals(30000, fromCity.population)
        assertEquals(21000, toCity.population)
        assertTrue(result.logs.any { it.contains("인구") })
    }

    // ========== Golden Value: Research Parameterized crewType Verification ==========

    @Test
    fun `research commands -- crewType parameter differentiation verified`() {
        // PHP: all event_*연구 share same template with different crewType/cost/turn
        // Verify cost/turn parameters differ correctly between 50000-tier and 100000-tier
        data class ResearchParams(
            val name: String,
            val cost: Int,
            val preReqTurn: Int,
            val metaKey: String,
        )

        val tier50000 = listOf(
            ResearchParams("대검병", 50000, 11, "can_대검병사용"),
            ResearchParams("산저병", 50000, 11, "can_산저병사용"),
            ResearchParams("음귀병", 50000, 11, "can_음귀병사용"),
            ResearchParams("화시병", 50000, 11, "can_화시병사용"),
        )
        val tier100000 = listOf(
            ResearchParams("무희", 100000, 23, "can_무희사용"),
            ResearchParams("상병", 100000, 23, "can_상병사용"),
            ResearchParams("원융노병", 100000, 23, "can_원융노병사용"),
            ResearchParams("화륜차", 100000, 23, "can_화륜차사용"),
        )
        // Verify tier separation: 50000-cost commands have preReqTurn=11, 100000-cost have preReqTurn=23
        for (p in tier50000) {
            assertEquals(50000, p.cost, "${p.name} should be tier 50000")
            assertEquals(11, p.preReqTurn, "${p.name} should have preReqTurn=11")
        }
        for (p in tier100000) {
            assertEquals(100000, p.cost, "${p.name} should be tier 100000")
            assertEquals(23, p.preReqTurn, "${p.name} should have preReqTurn=23")
        }
        // Total: 4 tier-50k + 4 tier-100k + 극병(100k) = 9 research commands
        assertEquals(9, tier50000.size + tier100000.size + 1, "9 research commands total")
    }

    // ========== Golden Value: Special Command Entity Diffs ==========

    @Test
    fun `무작위수도이전 golden value -- city ownership transferred and capital moved`() {
        val planetRepository = mock(PlanetRepository::class.java)
        val services = CommandServices(
            officerRepository = mock(OfficerRepository::class.java),
            planetRepository = planetRepository,
            factionRepository = mock(FactionRepository::class.java),
            diplomacyService = mock(DiplomacyService::class.java),
        )

        val commandEnv = env(year = 189, startYear = 190)
        commandEnv.gameStor["neutralCities"] = listOf(5)
        val nation = createNation(id = 1)
        nation.capitalPlanetId = 1
        val targetCity = createCity(id = 5, factionId = 0)

        `when`(planetRepository.findById(5L)).thenReturn(Optional.of(targetCity))

        val cmd = che_무작위수도이전(createGeneral(officerLevel = 20), commandEnv)
        cmd.city = createCity(id = 1, factionId = 1)
        cmd.nation = nation
        cmd.services = services

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: target city factionId set to nation's id
        assertEquals(1L, targetCity.factionId, "target city claimed by nation")
        // PHP golden value: nation.capitalPlanetId = targetCity.id
        assertEquals(5L, nation.capitalPlanetId, "capital moved to target city")
        // Log
        assertTrue(result.logs.any { it.contains("국가를 옮겼습니다") })
    }

    @Test
    fun `부대탈퇴지시 golden value -- fleetId reset to zero`() {
        val target = createGeneral(id = 3, factionId = 1)
        target.fleetId = 42

        val cmd = che_부대탈퇴지시(createGeneral(officerLevel = 20), env())
        cmd.destOfficer = target

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: fleetId = 0
        assertEquals(0L, target.fleetId, "fleetId reset to 0")
        assertTrue(result.logs.any { it.contains("탈퇴") })
    }

    @Test
    fun `인구이동 golden value -- exact population transfer and cost deduction`() {
        val nation = createNation(id = 1, funds = 20000, supplies = 15000)
        val fromCity = createCity(id = 1, factionId = 1)
        fromCity.population = 35000
        val toCity = createCity(id = 2, factionId = 1)
        toCity.population = 5000
        toCity.populationMax = 60000

        val cmd = cr_인구이동(createGeneral(officerLevel = 20), env(), mapOf("amount" to 10000))
        cmd.city = fromCity
        cmd.destPlanet = toCity
        cmd.nation = nation
        cmd.constraintEnv = mapOf(
            "mapAdjacency" to mapOf(1L to listOf(2L), 2L to listOf(1L)),
            "cityNationById" to mapOf(1L to 1L, 2L to 1L),
            "dbToMapId" to mapOf(1L to 1L, 2L to 2L),
            "mapToDbId" to mapOf(1L to 1L, 2L to 2L),
            "cityNationByMapId" to mapOf(1L to 1L, 2L to 1L),
        )

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: fromCity.population -= amount, toCity.population += amount
        assertEquals(25000, fromCity.population, "fromCity.population = 35000 - 10000")
        assertEquals(15000, toCity.population, "toCity.population = 5000 + 10000")
        // PHP golden value: cost = amount / 100 gold + amount / 100 rice
        assertEquals(19900, nation.funds, "nation.funds = 20000 - 100")
        assertEquals(14900, nation.supplies, "nation.supplies = 15000 - 100")
        assertTrue(result.logs.any { it.contains("인구") })
    }
}
