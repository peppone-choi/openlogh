package com.openlogh.command

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.command.general.*
import com.openlogh.engine.DiplomacyService
import com.openlogh.engine.LiteHashDRBG
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.FactionRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.Optional
import kotlin.random.Random

class GeneralMilitaryCommandTest {

    private fun createTestGeneral(
        funds: Int = 1000,
        supplies: Int = 1000,
        ships: Int = 0,
        shipClass: Short = 0,
        training: Short = 0,
        morale: Short = 0,
        leadership: Short = 50,
        command: Short = 50,
        intelligence: Short = 50,
        politics: Short = 50,
        administration: Short = 50,
        factionId: Long = 1,
        planetId: Long = 1,
        officerLevel: Short = 0,
        fleetId: Long = 0,
        experience: Int = 0,
        dedication: Int = 0,
        injury: Short = 0,
    ): Officer {
        return Officer(
            id = 1,
            sessionId = 1,
            name = "테스트장수",
            factionId = factionId,
            planetId = planetId,
            funds = funds,
            supplies = supplies,
            ships = ships,
            shipClass = shipClass,
            training = training,
            morale = morale,
            leadership = leadership,
            command = command,
            intelligence = intelligence,
            politics = politics,
            administration = administration,
            officerLevel = officerLevel,
            fleetId = fleetId,
            experience = experience,
            dedication = dedication,
            injury = injury,
            turnTime = OffsetDateTime.now(),
        )
    }

    private fun createTestCity(
        factionId: Long = 1,
        production: Int = 500,
        productionMax: Int = 1000,
        commerce: Int = 500,
        commerceMax: Int = 1000,
        security: Int = 500,
        securityMax: Int = 1000,
        orbitalDefense: Int = 500,
        orbitalDefenseMax: Int = 1000,
        fortress: Int = 500,
        fortressMax: Int = 1000,
        population: Int = 10000,
        populationMax: Int = 50000,
        approval: Float = 80f,
        supplyState: Short = 1,
        frontState: Short = 0,
    ): Planet {
        return Planet(
            id = 1,
            sessionId = 1,
            name = "테스트도시",
            factionId = factionId,
            production = production,
            productionMax = productionMax,
            commerce = commerce,
            commerceMax = commerceMax,
            security = security,
            securityMax = securityMax,
            orbitalDefense = orbitalDefense,
            orbitalDefenseMax = orbitalDefenseMax,
            fortress = fortress,
            fortressMax = fortressMax,
            population = population,
            populationMax = populationMax,
            approval = approval,
            supplyState = supplyState,
            frontState = frontState,
        )
    }

    private fun createTestNation(
        id: Long = 1,
        level: Short = 1,
        funds: Int = 10000,
        supplies: Int = 10000,
    ): Faction {
        return Faction(
            id = id,
            sessionId = 1,
            name = "테스트국가",
            color = "#FF0000",
            funds = funds,
            supplies = supplies,
            factionRank = level,
        )
    }

    private fun createTestEnv(
        year: Int = 200,
        month: Int = 1,
        startYear: Int = 190,
        develCost: Int = 100,
    ) = CommandEnv(
        year = year,
        month = month,
        startYear = startYear,
        sessionId = 1,
        realtimeMode = false,
        develCost = develCost,
    )

    private val fixedRng = Random(42)

    @Test
    fun `출병 should pass constraints and trigger battle`() {
        val general = createTestGeneral(ships = 1000, supplies = 100, factionId = 1, planetId = 1)
        val env = createTestEnv()
        env.gameStor["mapAdjacency"] = mapOf(1L to listOf(2L), 2L to listOf(1L))
        env.gameStor["cityNationById"] = mapOf(1L to 1L, 2L to 0L)
        env.gameStor["dbToMapId"] = mapOf(1L to 1L, 2L to 2L)
        env.gameStor["mapToDbId"] = mapOf(1L to 1L, 2L to 2L)
        env.gameStor["cityNationByMapId"] = mapOf(1L to 1L, 2L to 0L)
        env.gameStor["atWarNationIds"] = emptySet<Long>()

        val cmd = 출병(general, env)
        cmd.city = createTestCity(factionId = 1)
        cmd.nation = createTestNation(id = 1).apply { warState = 0 }
        cmd.destPlanet = createTestCity(factionId = 0).apply { id = 2 }

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.message!!.contains("\"battleTriggered\":true"))
        assertTrue(result.message!!.contains("\"targetCityId\":\"2\""))
        assertTrue(result.message!!.contains("\"rice\":-10"))
    }

    @Test
    fun `출병 should fail when destination is same city`() {
        val general = createTestGeneral(ships = 1000, supplies = 100, factionId = 1, planetId = 1)
        val env = createTestEnv()
        val cmd = 출병(general, env)
        cmd.city = createTestCity(factionId = 1)
        cmd.nation = createTestNation(id = 1)
        cmd.destPlanet = createTestCity(factionId = 0).apply { id = 1 }

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Fail)
        assertTrue((cond as ConstraintResult.Fail).reason.contains("같은 도시"))
    }

    @Test
    fun `집합 should fail for non troop leader`() {
        val general = createTestGeneral(factionId = 1, fleetId = 2)
        val env = createTestEnv()
        env.gameStor["troopMemberExistsByTroopId"] = mapOf(2L to true)
        val cmd = 집합(general, env)
        cmd.city = createTestCity(factionId = 1)

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Fail)
        assertTrue((cond as ConstraintResult.Fail).reason.contains("부대장"))
    }

    @Test
    fun `집합 should run with troop members`() {
        val general = createTestGeneral(factionId = 1, fleetId = 1)
        val env = createTestEnv()
        env.gameStor["troopMemberExistsByTroopId"] = mapOf(1L to true)
        val cmd = 집합(general, env)
        cmd.city = createTestCity(factionId = 1)

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.message!!.contains("\"troopAssembly\""))
        assertTrue(result.message!!.contains("\"troopLeaderId\":\"1\""))
    }

    @Test
    fun `집합 members not at city get moved`() {
        // Arrange: troop leader at city 1, member at city 2 (should be moved)
        val leader = createTestGeneral(factionId = 1, planetId = 1, fleetId = 1)
        val member = createTestGeneral(factionId = 1, planetId = 2, fleetId = 1).apply { id = 2L }

        val env = createTestEnv()
        env.gameStor["troopMemberExistsByTroopId"] = mapOf(1L to true)

        val officerRepository = mock(OfficerRepository::class.java)
        `when`(officerRepository.findByFleetId(1L)).thenReturn(listOf(leader, member))

        val mockServices = CommandServices(
            officerRepository = officerRepository,
            planetRepository = mock(PlanetRepository::class.java),
            factionRepository = mock(FactionRepository::class.java),
            diplomacyService = mock(DiplomacyService::class.java),
        )

        val cmd = 집합(leader, env)
        cmd.city = createTestCity(factionId = 1)
        cmd.services = mockServices

        val result = runBlocking { cmd.run(fixedRng) }

        assertTrue(result.success)
        // Member should have been moved to city 1
        assertEquals(1L, member.planetId, "Member not at city should be moved to leader city")
        // movedCount in message should reflect 1 member moved
        assertTrue(result.message!!.contains("\"movedCount\":1"))
        // destPlanetOfficers should contain the moved member
        assertTrue(cmd.destPlanetOfficers?.any { it.id == member.id } == true)
    }

    @Test
    fun `귀환 should fail in capital city`() {
        val general = createTestGeneral(factionId = 1, planetId = 1)
        val env = createTestEnv()
        val cmd = 귀환(general, env)
        cmd.nation = createTestNation(id = 1).apply { capitalPlanetId = 1 }

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Fail)
        assertTrue((cond as ConstraintResult.Fail).reason.contains("수도"))
    }

    @Test
    fun `귀환 should move district officer to officer city`() {
        val general = createTestGeneral(factionId = 1, planetId = 5, officerLevel = 3).apply {
            officerPlanet = 7
        }
        val env = createTestEnv()
        val cmd = 귀환(general, env)
        cmd.nation = createTestNation(id = 1).apply { capitalPlanetId = 2 }
        cmd.destPlanet = createTestCity().apply { name = "담당도시" }

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.message!!.contains("\"city\":7"))
    }

    @Test
    fun `접경귀환 should fail in occupied city`() {
        val general = createTestGeneral(factionId = 1, planetId = 1)
        val env = createTestEnv()
        val cmd = 접경귀환(general, env)
        cmd.city = createTestCity(factionId = 1)

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Fail)
        assertTrue((cond as ConstraintResult.Fail).reason.contains("아군 도시"))
    }

    @Test
    fun `접경귀환 should move to nearest supplied friendly city`() {
        val general = createTestGeneral(factionId = 1, planetId = 1)
        val env = createTestEnv()
        env.gameStor["mapAdjacency"] = mapOf(
            1L to listOf(2L),
            2L to listOf(1L, 3L),
            3L to listOf(2L),
        )
        env.gameStor["cityNationById"] = mapOf(1L to 2L, 2L to 1L, 3L to 1L)
        env.gameStor["citySupplyStateById"] = mapOf(2L to 1, 3L to 1)

        val cmd = 접경귀환(general, env)
        cmd.city = createTestCity(factionId = 2)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.message!!.contains("\"city\":2"))
    }

    @Test
    fun `강행 should fail when route does not exist`() {
        val general = createTestGeneral(funds = 1000, supplies = 1000, planetId = 1)
        val env = createTestEnv()
        val cmd = 강행(general, env)
        cmd.destPlanet = createTestCity().apply { id = 2 }

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Fail)
        assertTrue((cond as ConstraintResult.Fail).reason.contains("거리"))
    }

    @Test
    fun `강행 should move city and consume gold`() {
        val general = createTestGeneral(funds = 1000, supplies = 1000, planetId = 1, training = 60, morale = 60)
        val env = createTestEnv(develCost = 100)
        env.gameStor["mapAdjacency"] = mapOf(1L to listOf(2L), 2L to listOf(1L))
        env.gameStor["cityNationById"] = mapOf(1L to 1L, 2L to 1L)
        env.gameStor["dbToMapId"] = mapOf(1L to 1L, 2L to 2L)
        env.gameStor["mapToDbId"] = mapOf(1L to 1L, 2L to 2L)
        env.gameStor["cityNationByMapId"] = mapOf(1L to 1L, 2L to 1L)

        val cmd = 강행(general, env)
        cmd.destPlanet = createTestCity().apply { id = 2 }

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.message!!.contains("\"planetId\":\"2\""))
        assertTrue(result.message!!.contains("\"gold\":-500"))
        assertTrue(result.message!!.contains("\"train\":-5"))
        assertTrue(result.message!!.contains("\"atmos\":-5"))
    }

    @Test
    fun `거병 should fail for non neutral general`() {
        val general = createTestGeneral(factionId = 1)
        val env = createTestEnv(year = 189, startYear = 190)
        val cmd = 거병(general, env)

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Fail)
        assertTrue((cond as ConstraintResult.Fail).reason.contains("재야"))
    }

    @Test
    fun `거병 should create wandering nation on success`() {
        val general = createTestGeneral(factionId = 0, planetId = 1)
        val env = createTestEnv(year = 189, startYear = 190)
        val cmd = 거병(general, env)
        cmd.city = createTestCity(factionId = 0)

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.message!!.contains("\"createWanderingNation\":true"))
        assertTrue(result.message!!.contains("\"officerLevel\":20"))
    }

    @Test
    fun `전투태세 should fail when train margin is insufficient`() {
        val general = createTestGeneral(factionId = 1, ships = 1000, training = 90, morale = 50, funds = 1000)
        val env = createTestEnv()
        val cmd = 전투태세(general, env)
        cmd.city = createTestCity(factionId = 1)

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Fail)
        assertTrue((cond as ConstraintResult.Fail).reason.contains("훈련"))
    }

    @Test
    fun `전투태세 should set minimum train and atmos on run`() {
        val general = createTestGeneral(factionId = 1, ships = 1000, shipClass = 1, training = 50, morale = 50, funds = 1000)
        val env = createTestEnv()
        val cmd = 전투태세(general, env)
        cmd.city = createTestCity(factionId = 1)

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.message!!.contains("\"setMin\":95"))
        assertTrue(result.message!!.contains("\"leadershipExp\":3"))
    }

    @Test
    fun `화계 should fail against neutral destination city`() {
        val general = createTestGeneral(factionId = 1, planetId = 1, funds = 1000, supplies = 1000, intelligence = 100)
        val env = createTestEnv()
        val cmd = 화계(general, env)
        cmd.city = createTestCity(factionId = 1, supplyState = 1)
        cmd.destPlanet = createTestCity(factionId = 0).apply { id = 2 }

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Fail)
        assertTrue((cond as ConstraintResult.Fail).reason.contains("공백지"))
    }

    @Test
    fun `화계 should execute with fixed rng and consume resources`() {
        val general = createTestGeneral(factionId = 1, planetId = 1, funds = 5000, supplies = 5000, intelligence = 100)
        val env = createTestEnv()
        env.gameStor["mapAdjacency"] = mapOf(1L to listOf(2L), 2L to listOf(1L))
        val cmd = 화계(general, env)
        cmd.city = createTestCity(factionId = 1, supplyState = 1)
        cmd.destPlanet = createTestCity(factionId = 2, production = 600, commerce = 600).apply { id = 2 }

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.message!!.contains("\"gold\":-500"))
        assertTrue(result.message!!.contains("\"rice\":-500"))
        assertTrue(result.message!!.contains("\"destCityChanges\""))
    }

    @Test
    fun `첩보 should fail for friendly destination city`() {
        // cost = develCost(100) * 3 = 300
        val general = createTestGeneral(factionId = 1, funds = 500, supplies = 500)
        val env = createTestEnv()
        val cmd = 첩보(general, env)
        cmd.destPlanet = createTestCity(factionId = 1).apply { id = 2 }

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Fail)
        assertTrue((cond as ConstraintResult.Fail).reason.contains("아군 도시"))
    }

    @Test
    fun `첩보 should return spy result and consume resources`() {
        // cost = develCost(100) * 3 = 300
        val general = createTestGeneral(factionId = 1, funds = 500, supplies = 500)
        val env = createTestEnv()
        val cmd = 첩보(general, env)
        cmd.destPlanet = createTestCity(factionId = 2, population = 12000, approval = 75f).apply { id = 2 }

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.message!!.contains("\"spyResult\""))
        assertTrue(result.message!!.contains("\"gold\":-300"))
        assertTrue(result.message!!.contains("\"rice\":-300"))
    }

    @Test
    fun `선동 should fail against neutral destination city`() {
        val general = createTestGeneral(factionId = 1, planetId = 1, funds = 1000, supplies = 1000, leadership = 100)
        val env = createTestEnv()
        val cmd = 선동(general, env)
        cmd.city = createTestCity(factionId = 1, supplyState = 1)
        cmd.destPlanet = createTestCity(factionId = 0).apply { id = 2 }

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Fail)
        assertTrue((cond as ConstraintResult.Fail).reason.contains("공백지"))
    }

    @Test
    fun `선동 should succeed and modify security approval with fixed rng`() {
        val general = createTestGeneral(factionId = 1, planetId = 1, funds = 1000, supplies = 1000, leadership = 100)
        val env = createTestEnv()
        env.gameStor["mapAdjacency"] = mapOf(1L to listOf(2L), 2L to listOf(1L))
        val cmd = 선동(general, env)
        cmd.city = createTestCity(factionId = 1, supplyState = 1)
        cmd.destPlanet = createTestCity(factionId = 2, security = 700, approval = 90f).apply { id = 2 }

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.message!!.contains("\"destCityChanges\""))
        assertTrue(result.message!!.contains("\"security\""))
        assertTrue(result.message!!.contains("\"approval\""))
    }

    @Test
    fun `탈취 should fail against neutral destination city`() {
        val general = createTestGeneral(factionId = 1, planetId = 1, funds = 1000, supplies = 1000, command = 100)
        val env = createTestEnv()
        val cmd = 탈취(general, env)
        cmd.city = createTestCity(factionId = 1, supplyState = 1)
        cmd.destPlanet = createTestCity(factionId = 0).apply { id = 2 }

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Fail)
        assertTrue((cond as ConstraintResult.Fail).reason.contains("공백지"))
    }

    @Test
    fun `탈취 should succeed and include destination city changes`() {
        val general = createTestGeneral(factionId = 1, planetId = 1, funds = 1000, supplies = 1000, command = 100)
        val env = createTestEnv()
        env.gameStor["mapAdjacency"] = mapOf(1L to listOf(2L), 2L to listOf(1L))
        val cmd = 탈취(general, env)
        cmd.city = createTestCity(factionId = 1, supplyState = 1)
        cmd.destPlanet = createTestCity(factionId = 2, production = 700, productionMax = 1000, commerce = 700, commerceMax = 1000).apply {
            id = 2
            level = 3
        }

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.message!!.contains("\"destCityChanges\""))
        assertTrue(result.message!!.contains("\"commerce\""))
        assertTrue(result.message!!.contains("\"production\""))
    }

    @Test
    fun `파괴 should fail against neutral destination city`() {
        val general = createTestGeneral(factionId = 1, planetId = 1, funds = 1000, supplies = 1000, command = 100)
        val env = createTestEnv()
        val cmd = 파괴(general, env)
        cmd.city = createTestCity(factionId = 1, supplyState = 1)
        cmd.destPlanet = createTestCity(factionId = 0).apply { id = 2 }

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Fail)
        assertTrue((cond as ConstraintResult.Fail).reason.contains("공백지"))
    }

    @Test
    fun `파괴 should succeed and reduce defense and fortress`() {
        val general = createTestGeneral(factionId = 1, planetId = 1, funds = 1000, supplies = 1000, command = 100)
        val env = createTestEnv()
        env.gameStor["mapAdjacency"] = mapOf(1L to listOf(2L), 2L to listOf(1L))
        val cmd = 파괴(general, env)
        cmd.city = createTestCity(factionId = 1, supplyState = 1)
        cmd.destPlanet = createTestCity(factionId = 2, orbitalDefense = 700, fortress = 700).apply { id = 2 }

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.message!!.contains("\"destCityChanges\""))
        assertTrue(result.message!!.contains("\"orbitalDefense\""))
        assertTrue(result.message!!.contains("\"fortress\""))
    }

    @Test
    fun `방랑 should fail for non lord`() {
        val general = createTestGeneral(factionId = 1, officerLevel = 5)
        val env = createTestEnv()
        val cmd = 방랑(general, env)

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Fail)
        assertTrue((cond as ConstraintResult.Fail).reason.contains("군주"))
    }

    @Test
    fun `방랑 should succeed and become wandering nation`() {
        val general = createTestGeneral(factionId = 1, officerLevel = 20)
        val env = createTestEnv(year = 200, startYear = 190)
        val cmd = 방랑(general, env)

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.message!!.contains("\"becomeWanderer\":true"))
        assertTrue(result.message!!.contains("\"releaseAllCities\":true"))
    }

    // ================================================================
    // Golden value parity tests (Phase 07 Plan 01)
    // Fixed seed: "golden_parity_seed" -> deterministic output
    // ================================================================

    private val mapper = jacksonObjectMapper()
    private val GOLDEN_SEED = "golden_parity_seed"

    @Test
    fun `parity 출병 golden value matches PHP-traced expectation`() {
        val general = createTestGeneral(ships = 1000, supplies = 100, factionId = 1, planetId = 1)
        val env = createTestEnv()
        env.gameStor["mapAdjacency"] = mapOf(1L to listOf(2L), 2L to listOf(1L))
        env.gameStor["cityNationById"] = mapOf(1L to 1L, 2L to 0L)
        env.gameStor["dbToMapId"] = mapOf(1L to 1L, 2L to 2L)
        env.gameStor["mapToDbId"] = mapOf(1L to 1L, 2L to 2L)
        env.gameStor["cityNationByMapId"] = mapOf(1L to 1L, 2L to 0L)
        env.gameStor["atWarNationIds"] = emptySet<Long>()

        val cmd = 출병(general, env)
        cmd.city = createTestCity(factionId = 1)
        cmd.nation = createTestNation(id = 1).apply { warState = 0 }
        cmd.destPlanet = createTestCity(factionId = 0).apply { id = 2 }

        val result = runBlocking { cmd.run(LiteHashDRBG.build(GOLDEN_SEED)) }
        assertTrue(result.success)

        val json = mapper.readTree(result.message)
        assertEquals(-10, json["statChanges"]["rice"].asInt())
        assertEquals(0, json["dexChanges"]["crewType"].asInt())
        assertEquals(10, json["dexChanges"]["amount"].asInt())
        assertTrue(json["battleTriggered"].asBoolean())
        assertEquals("2", json["targetCityId"].asText())
        assertTrue(json["tryUniqueLottery"].asBoolean())

        // Log color tag parity
        assertTrue(result.logs[0].contains("<G><b>2</b></>"))
        assertTrue(result.logs[0].contains("<1>200년 01월</>"))

        // Determinism
        val general2 = createTestGeneral(ships = 1000, supplies = 100, factionId = 1, planetId = 1)
        val env2 = createTestEnv()
        env2.gameStor.putAll(env.gameStor)
        val cmd2 = 출병(general2, env2)
        cmd2.city = createTestCity(factionId = 1)
        cmd2.nation = createTestNation(id = 1).apply { warState = 0 }
        cmd2.destPlanet = createTestCity(factionId = 0).apply { id = 2 }
        val result2 = runBlocking { cmd2.run(LiteHashDRBG.build(GOLDEN_SEED)) }
        assertEquals(result.message, result2.message)
    }

    @Test
    fun `parity 출병 constraint fails when crew is zero`() {
        val general = createTestGeneral(ships = 0, factionId = 1, planetId = 1)
        val env = createTestEnv()
        val cmd = 출병(general, env)
        cmd.city = createTestCity(factionId = 1)
        cmd.nation = createTestNation(id = 1)
        cmd.destPlanet = createTestCity(factionId = 0).apply { id = 2 }
        assertTrue(cmd.checkFullCondition() is ConstraintResult.Fail)
    }

    @Test
    fun `parity 출병 constraint fails when same city`() {
        val general = createTestGeneral(ships = 1000, supplies = 100, factionId = 1, planetId = 1)
        val env = createTestEnv()
        val cmd = 출병(general, env)
        cmd.city = createTestCity(factionId = 1)
        cmd.nation = createTestNation(id = 1)
        cmd.destPlanet = createTestCity(factionId = 0).apply { id = 1 }
        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Fail)
    }

    @Test
    fun `parity 이동 golden value matches PHP-traced expectation`() {
        val general = createTestGeneral(funds = 1000, supplies = 1000, planetId = 1, morale = 80)
        val env = createTestEnv(develCost = 100)
        env.gameStor["mapAdjacency"] = mapOf(1L to listOf(2L), 2L to listOf(1L))

        val cmd = 이동(general, env)
        cmd.destPlanet = createTestCity().apply { id = 2; name = "낙양" }

        val result = runBlocking { cmd.run(LiteHashDRBG.build(GOLDEN_SEED)) }
        assertTrue(result.success)

        val json = mapper.readTree(result.message)
        assertEquals("2", json["statChanges"]["planetId"].asText())
        assertEquals(-100, json["statChanges"]["gold"].asInt())
        assertEquals(-5, json["statChanges"]["atmos"].asInt())
        assertEquals(50, json["statChanges"]["experience"].asInt())
        assertEquals(1, json["statChanges"]["leadershipExp"].asInt())
        assertTrue(json["tryUniqueLottery"].asBoolean())

        // Log color tag parity
        assertTrue(result.logs[0].contains("<G><b>낙양</b></>"))
        assertTrue(result.logs[0].contains("이동"))
        assertTrue(result.logs[0].contains("<1>"))
    }

    @Test
    fun `parity 이동 constraint fails when not adjacent city`() {
        val general = createTestGeneral(funds = 1000, planetId = 1)
        val env = createTestEnv()
        env.gameStor["mapAdjacency"] = mapOf(1L to listOf(2L), 3L to listOf(4L))

        val cmd = 이동(general, env)
        cmd.destPlanet = createTestCity().apply { id = 3 }

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Fail)
    }

    @Test
    fun `parity 이동 constraint fails when same city`() {
        val general = createTestGeneral(funds = 1000, planetId = 1)
        val env = createTestEnv()

        val cmd = 이동(general, env)
        cmd.destPlanet = createTestCity().apply { id = 1 }

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Fail)
    }

    @Test
    fun `parity 귀환 golden value matches PHP-traced expectation`() {
        val general = createTestGeneral(factionId = 1, planetId = 5, officerLevel = 3).apply {
            officerPlanet = 7
        }
        val cmd = 귀환(general, createTestEnv())
        cmd.nation = createTestNation(id = 1).apply { capitalPlanetId = 2 }
        cmd.destPlanet = createTestCity().apply { name = "담당도시" }

        val result = runBlocking { cmd.run(LiteHashDRBG.build(GOLDEN_SEED)) }
        assertTrue(result.success)

        val json = mapper.readTree(result.message)
        assertEquals(7, json["statChanges"]["city"].asInt())
        assertEquals(70, json["statChanges"]["experience"].asInt())
        assertEquals(100, json["statChanges"]["dedication"].asInt())
        assertEquals(1, json["statChanges"]["leadershipExp"].asInt())

        // Log color tag parity
        assertTrue(result.logs[0].contains("<G><b>담당도시</b></>"))
        assertTrue(result.logs[0].contains("귀환"))
    }

    @Test
    fun `parity 접경귀환 golden value matches PHP-traced expectation`() {
        val general = createTestGeneral(factionId = 1, planetId = 1)
        val env = createTestEnv()
        env.gameStor["mapAdjacency"] = mapOf(1L to listOf(2L), 2L to listOf(1L, 3L), 3L to listOf(2L))
        env.gameStor["cityNationById"] = mapOf(1L to 2L, 2L to 1L, 3L to 1L)
        env.gameStor["citySupplyStateById"] = mapOf(2L to 1, 3L to 1)

        val cmd = 접경귀환(general, env)
        cmd.city = createTestCity(factionId = 2)

        val result = runBlocking { cmd.run(LiteHashDRBG.build(GOLDEN_SEED)) }
        assertTrue(result.success)

        val json = mapper.readTree(result.message)
        assertEquals(2, json["statChanges"]["city"].asInt())

        // Log color tag parity
        assertTrue(result.logs[0].contains("접경귀환"))
    }

    @Test
    fun `parity 접경귀환 constraint fails in occupied city`() {
        val general = createTestGeneral(factionId = 1, planetId = 1)
        val cmd = 접경귀환(general, createTestEnv())
        cmd.city = createTestCity(factionId = 1)
        assertTrue(cmd.checkFullCondition() is ConstraintResult.Fail)
    }

    @Test
    fun `parity 강행 golden value matches PHP-traced expectation`() {
        val general = createTestGeneral(funds = 1000, supplies = 1000, planetId = 1, training = 60, morale = 60)
        val env = createTestEnv(develCost = 100)
        env.gameStor["mapAdjacency"] = mapOf(1L to listOf(2L), 2L to listOf(1L))
        env.gameStor["cityNationById"] = mapOf(1L to 1L, 2L to 1L)
        env.gameStor["dbToMapId"] = mapOf(1L to 1L, 2L to 2L)
        env.gameStor["mapToDbId"] = mapOf(1L to 1L, 2L to 2L)
        env.gameStor["cityNationByMapId"] = mapOf(1L to 1L, 2L to 1L)

        val cmd = 강행(general, env)
        cmd.destPlanet = createTestCity().apply { id = 2 }

        val result = runBlocking { cmd.run(LiteHashDRBG.build(GOLDEN_SEED)) }
        assertTrue(result.success)

        val json = mapper.readTree(result.message)
        assertEquals("2", json["statChanges"]["planetId"].asText())
        assertEquals(-500, json["statChanges"]["gold"].asInt())
        assertEquals(-5, json["statChanges"]["train"].asInt())
        assertEquals(-5, json["statChanges"]["atmos"].asInt())
        assertEquals(100, json["statChanges"]["experience"].asInt())
        assertEquals(1, json["statChanges"]["leadershipExp"].asInt())
        assertTrue(json["tryUniqueLottery"].asBoolean())

        // Log color tag parity
        assertTrue(result.logs[0].contains("<G><b>테스트도시</b></>"))
        assertTrue(result.logs[0].contains("강행"))
    }

    @Test
    fun `parity 강행 constraint fails when no route`() {
        val general = createTestGeneral(funds = 1000, supplies = 1000, planetId = 1)
        val cmd = 강행(general, createTestEnv())
        cmd.destPlanet = createTestCity().apply { id = 2 }
        assertTrue(cmd.checkFullCondition() is ConstraintResult.Fail)
    }

    @Test
    fun `parity 거병 golden value matches PHP-traced expectation`() {
        val general = createTestGeneral(factionId = 0, planetId = 1)
        val env = createTestEnv(year = 189, startYear = 190)
        val cmd = 거병(general, env)
        cmd.city = createTestCity(factionId = 0)

        val result = runBlocking { cmd.run(LiteHashDRBG.build(GOLDEN_SEED)) }
        assertTrue(result.success)

        val json = mapper.readTree(result.message)
        assertEquals(100, json["statChanges"]["experience"].asInt())
        assertEquals(100, json["statChanges"]["dedication"].asInt())
        assertEquals(20, json["statChanges"]["officerLevel"].asInt())
        assertTrue(json["nationChanges"]["createWanderingNation"].asBoolean())
        assertEquals("테스트장수", json["nationChanges"]["factionName"].asText())

        // Log color tag parity
        assertTrue(result.logs[0].contains("거병에 성공"))
        assertTrue(result.logs[0].contains("<1>189년 01월</>"))
    }

    @Test
    fun `parity 거병 constraint fails for non-neutral general`() {
        val general = createTestGeneral(factionId = 1)
        val cmd = 거병(general, createTestEnv(year = 189, startYear = 190))
        assertTrue(cmd.checkFullCondition() is ConstraintResult.Fail)
    }

    @Test
    fun `parity 전투태세 golden value matches PHP-traced expectation`() {
        val general = createTestGeneral(factionId = 1, ships = 1000, shipClass = 1, training = 50, morale = 50, funds = 1000)
        val cmd = 전투태세(general, createTestEnv())
        cmd.city = createTestCity(factionId = 1)

        val result = runBlocking { cmd.run(LiteHashDRBG.build(GOLDEN_SEED)) }
        assertTrue(result.success)

        val json = mapper.readTree(result.message)
        assertEquals(-30, json["statChanges"]["gold"].asInt())
        assertEquals(95, json["statChanges"]["train"]["setMin"].asInt())
        assertEquals(95, json["statChanges"]["atmos"]["setMin"].asInt())
        assertEquals(300, json["statChanges"]["experience"].asInt())
        assertEquals(210, json["statChanges"]["dedication"].asInt())
        assertEquals(3, json["statChanges"]["leadershipExp"].asInt())
        assertEquals(1, json["dexChanges"]["crewType"].asInt())
        assertEquals(30, json["dexChanges"]["amount"].asInt())
        assertEquals(3, json["battleStanceTerm"].asInt())
        assertTrue(json["completed"].asBoolean())

        // Log color tag parity
        assertTrue(result.logs[0].contains("전투태세 완료"))
    }

    @Test
    fun `parity 화계 golden value with fixed seed`() {
        val general = createTestGeneral(factionId = 1, planetId = 1, funds = 5000, supplies = 5000, intelligence = 100)
        val env = createTestEnv()
        env.gameStor["mapAdjacency"] = mapOf(1L to listOf(2L), 2L to listOf(1L))
        val cmd = 화계(general, env)
        cmd.city = createTestCity(factionId = 1, supplyState = 1)
        cmd.destPlanet = createTestCity(factionId = 2, production = 600, commerce = 600).apply { id = 2 }

        val result = runBlocking { cmd.run(LiteHashDRBG.build(GOLDEN_SEED)) }
        // With this seed, fire attack fails
        assertFalse(result.success)

        val json = mapper.readTree(result.message)
        assertEquals(-500, json["statChanges"]["gold"].asInt())
        assertEquals(-500, json["statChanges"]["rice"].asInt())
        assertEquals(14, json["statChanges"]["experience"].asInt())
        assertEquals(30, json["statChanges"]["dedication"].asInt())
        assertEquals(1, json["statChanges"]["intelExp"].asInt())

        // Log color tag parity
        assertTrue(result.logs[0].contains("<G><b>테스트도시</b></>"))
        assertTrue(result.logs[0].contains("화계가 실패"))
    }

    @Test
    fun `parity 화계 constraint fails against neutral city`() {
        val general = createTestGeneral(factionId = 1, funds = 1000, supplies = 1000, intelligence = 100)
        val cmd = 화계(general, createTestEnv())
        cmd.city = createTestCity(factionId = 1, supplyState = 1)
        cmd.destPlanet = createTestCity(factionId = 0).apply { id = 2 }
        assertTrue(cmd.checkFullCondition() is ConstraintResult.Fail)
    }

    @Test
    fun `parity 첩보 golden value matches PHP-traced expectation`() {
        val general = createTestGeneral(factionId = 1, funds = 500, supplies = 500)
        val cmd = 첩보(general, createTestEnv())
        cmd.destPlanet = createTestCity(factionId = 2, population = 12000, approval = 75f).apply { id = 2 }

        val result = runBlocking { cmd.run(LiteHashDRBG.build(GOLDEN_SEED)) }
        assertTrue(result.success)

        val json = mapper.readTree(result.message)
        assertEquals(-300, json["statChanges"]["gold"].asInt())
        assertEquals(-300, json["statChanges"]["rice"].asInt())
        assertEquals(62, json["statChanges"]["experience"].asInt())
        assertEquals(40, json["statChanges"]["dedication"].asInt())
        assertEquals(1, json["statChanges"]["leadershipExp"].asInt())
        assertNotNull(json["spyResult"])
        assertEquals(2, json["spyResult"]["destCityId"].asInt())

        // Log parity: spy result includes city info
        assertTrue(result.logs.any { it.contains("12,000") })
        assertTrue(result.logs.any { it.contains("75.0") })
    }

    @Test
    fun `parity 첩보 constraint fails for friendly city`() {
        val general = createTestGeneral(factionId = 1, funds = 500, supplies = 500)
        val cmd = 첩보(general, createTestEnv())
        cmd.destPlanet = createTestCity(factionId = 1).apply { id = 2 }
        assertTrue(cmd.checkFullCondition() is ConstraintResult.Fail)
    }

    @Test
    fun `parity 선동 golden value with fixed seed`() {
        val general = createTestGeneral(factionId = 1, planetId = 1, funds = 1000, supplies = 1000, leadership = 100)
        val env = createTestEnv()
        env.gameStor["mapAdjacency"] = mapOf(1L to listOf(2L), 2L to listOf(1L))
        val cmd = 선동(general, env)
        cmd.city = createTestCity(factionId = 1, supplyState = 1)
        cmd.destPlanet = createTestCity(factionId = 2, security = 700, approval = 90f).apply { id = 2 }

        val result = runBlocking { cmd.run(LiteHashDRBG.build(GOLDEN_SEED)) }
        // With this seed, agitation fails
        assertFalse(result.success)

        val json = mapper.readTree(result.message)
        assertEquals(-500, json["statChanges"]["gold"].asInt())
        assertEquals(-500, json["statChanges"]["rice"].asInt())
        assertEquals(14, json["statChanges"]["experience"].asInt())
        assertEquals(30, json["statChanges"]["dedication"].asInt())

        // Log color tag parity
        assertTrue(result.logs[0].contains("선동이 실패"))
    }

    @Test
    fun `parity 선동 constraint fails against neutral city`() {
        val general = createTestGeneral(factionId = 1, leadership = 100)
        val cmd = 선동(general, createTestEnv())
        cmd.city = createTestCity(factionId = 1, supplyState = 1)
        cmd.destPlanet = createTestCity(factionId = 0).apply { id = 2 }
        assertTrue(cmd.checkFullCondition() is ConstraintResult.Fail)
    }

    @Test
    fun `parity 탈취 golden value with fixed seed`() {
        val general = createTestGeneral(factionId = 1, planetId = 1, funds = 1000, supplies = 1000, command = 100)
        val env = createTestEnv()
        env.gameStor["mapAdjacency"] = mapOf(1L to listOf(2L), 2L to listOf(1L))
        val cmd = 탈취(general, env)
        cmd.city = createTestCity(factionId = 1, supplyState = 1)
        cmd.destPlanet = createTestCity(factionId = 2, production = 700, productionMax = 1000, commerce = 700, commerceMax = 1000).apply {
            id = 2; level = 3
        }

        val result = runBlocking { cmd.run(LiteHashDRBG.build(GOLDEN_SEED)) }
        // With this seed, plunder fails
        assertFalse(result.success)

        val json = mapper.readTree(result.message)
        assertEquals(-500, json["statChanges"]["gold"].asInt())
        assertEquals(-500, json["statChanges"]["rice"].asInt())
        assertEquals(14, json["statChanges"]["experience"].asInt())
        assertEquals(30, json["statChanges"]["dedication"].asInt())
        assertEquals(1, json["statChanges"]["strengthExp"].asInt())

        // Log color tag parity
        assertTrue(result.logs[0].contains("탈취가 실패"))
    }

    @Test
    fun `parity 탈취 constraint fails against neutral city`() {
        val general = createTestGeneral(factionId = 1, command = 100)
        val cmd = 탈취(general, createTestEnv())
        cmd.city = createTestCity(factionId = 1, supplyState = 1)
        cmd.destPlanet = createTestCity(factionId = 0).apply { id = 2 }
        assertTrue(cmd.checkFullCondition() is ConstraintResult.Fail)
    }

    @Test
    fun `parity 파괴 golden value with fixed seed`() {
        val general = createTestGeneral(factionId = 1, planetId = 1, funds = 1000, supplies = 1000, command = 100)
        val env = createTestEnv()
        env.gameStor["mapAdjacency"] = mapOf(1L to listOf(2L), 2L to listOf(1L))
        val cmd = 파괴(general, env)
        cmd.city = createTestCity(factionId = 1, supplyState = 1)
        cmd.destPlanet = createTestCity(factionId = 2, orbitalDefense = 700, fortress = 700).apply { id = 2 }

        val result = runBlocking { cmd.run(LiteHashDRBG.build(GOLDEN_SEED)) }
        // With this seed, destruction fails
        assertFalse(result.success)

        val json = mapper.readTree(result.message)
        assertEquals(-500, json["statChanges"]["gold"].asInt())
        assertEquals(-500, json["statChanges"]["rice"].asInt())
        assertEquals(14, json["statChanges"]["experience"].asInt())
        assertEquals(30, json["statChanges"]["dedication"].asInt())
        assertEquals(1, json["statChanges"]["strengthExp"].asInt())

        // Log color tag parity
        assertTrue(result.logs[0].contains("파괴가 실패"))
    }

    @Test
    fun `parity 파괴 constraint fails against neutral city`() {
        val general = createTestGeneral(factionId = 1, command = 100)
        val cmd = 파괴(general, createTestEnv())
        cmd.city = createTestCity(factionId = 1, supplyState = 1)
        cmd.destPlanet = createTestCity(factionId = 0).apply { id = 2 }
        assertTrue(cmd.checkFullCondition() is ConstraintResult.Fail)
    }

    @Test
    fun `parity 요양 golden value matches PHP-traced expectation`() {
        val general = createTestGeneral(injury = 50)
        val cmd = 요양(general, createTestEnv())

        val result = runBlocking { cmd.run(LiteHashDRBG.build(GOLDEN_SEED)) }
        assertTrue(result.success)

        val json = mapper.readTree(result.message)
        assertEquals(-50, json["statChanges"]["injury"].asInt())
        assertEquals(10, json["statChanges"]["experience"].asInt())
        assertEquals(7, json["statChanges"]["dedication"].asInt())

        // Log parity
        assertTrue(result.logs[0].contains("요양"))
        assertTrue(result.logs[0].contains("<1>200년 01월</>"))
    }

    @Test
    fun `parity 방랑 golden value matches PHP-traced expectation`() {
        val general = createTestGeneral(factionId = 1, officerLevel = 20)
        val cmd = 방랑(general, createTestEnv(year = 200, startYear = 190))

        val result = runBlocking { cmd.run(LiteHashDRBG.build(GOLDEN_SEED)) }
        assertTrue(result.success)

        val json = mapper.readTree(result.message)
        assertTrue(json["becomeWanderer"].asBoolean())
        assertTrue(json["releaseAllCities"].asBoolean())
        assertTrue(json["resetDiplomacy"].asBoolean())
        assertEquals("테스트장수", json["nationChanges"]["name"].asText())
        assertEquals(0, json["nationChanges"]["level"].asInt())

        // Log parity
        assertTrue(result.logs[0].contains("방랑의 길"))
    }

    @Test
    fun `parity 집합 golden value matches PHP-traced expectation`() {
        val general = createTestGeneral(factionId = 1, planetId = 1).apply { fleetId = 1 }
        val env = createTestEnv()
        env.gameStor["troopMemberExistsByTroopId"] = mapOf(1L to true)
        val cmd = 집합(general, env)
        cmd.city = createTestCity(factionId = 1)

        val result = runBlocking { cmd.run(LiteHashDRBG.build(GOLDEN_SEED)) }
        assertTrue(result.success)

        val json = mapper.readTree(result.message)
        assertEquals(70, json["statChanges"]["experience"].asInt())
        assertEquals(100, json["statChanges"]["dedication"].asInt())
        assertEquals(1, json["statChanges"]["leadershipExp"].asInt())
        assertEquals("1", json["troopAssembly"]["troopLeaderId"].asText())
        assertEquals("1", json["troopAssembly"]["destinationCityId"].asText())
        assertEquals(0, json["troopAssembly"]["movedCount"].asInt())

        // Log color tag parity
        assertTrue(result.logs[0].contains("<G><b>테스트도시</b></>"))
        assertTrue(result.logs[0].contains("집합"))
    }

    // ================================================================
    // Kotlin-only commands (순찰, 요격, 좌표이동) — basic operation tests
    // No PHP golden value comparison; verify constraint + entity mutation + log
    // ================================================================

    @Test
    fun `kotlin-only 순찰 basic operation and entity mutation`() {
        val general = createTestGeneral(factionId = 1, ships = 100, supplies = 100)
        val cmd = 순찰(general, createTestEnv())
        cmd.city = createTestCity(factionId = 1)

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(LiteHashDRBG.build(GOLDEN_SEED)) }
        assertTrue(result.success)

        val json = mapper.readTree(result.message)
        assertEquals(-30, json["statChanges"]["rice"].asInt())

        // Entity mutation: lastTurn set
        assertEquals("순찰", general.lastTurn["action"])
        assertEquals(1L, general.lastTurn["patrolCityId"])

        // Log generated
        assertTrue(result.logs[0].contains("순찰을 시작"))
    }

    @Test
    fun `kotlin-only 순찰 constraint fails without crew`() {
        val general = createTestGeneral(factionId = 1, ships = 0)
        val cmd = 순찰(general, createTestEnv())
        cmd.city = createTestCity(factionId = 1)
        assertTrue(cmd.checkFullCondition() is ConstraintResult.Fail)
    }

    @Test
    fun `kotlin-only 요격 basic operation and entity mutation`() {
        val general = createTestGeneral(factionId = 1, ships = 100, supplies = 100)
        val cmd = 요격(general, createTestEnv())
        cmd.city = createTestCity(factionId = 1)
        cmd.destPlanet = createTestCity(factionId = 2).apply { id = 2; name = "적도시" }

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(LiteHashDRBG.build(GOLDEN_SEED)) }
        assertTrue(result.success)

        val json = mapper.readTree(result.message)
        assertEquals(-50, json["statChanges"]["rice"].asInt())

        // Entity mutation: lastTurn set
        assertEquals("요격", general.lastTurn["action"])
        assertEquals(2L, general.lastTurn["interceptionTargetCityId"])
        assertEquals(1L, general.lastTurn["originCityId"])

        // Log generated
        assertTrue(result.logs[0].contains("매복"))
        assertTrue(result.logs[0].contains("적도시"))
    }

    @Test
    fun `kotlin-only 요격 constraint fails without crew`() {
        val general = createTestGeneral(factionId = 1, ships = 0, supplies = 100)
        val cmd = 요격(general, createTestEnv())
        cmd.city = createTestCity(factionId = 1)
        assertTrue(cmd.checkFullCondition() is ConstraintResult.Fail)
    }

    @Test
    fun `kotlin-only 좌표이동 basic operation and entity mutation`() {
        val general = createTestGeneral(factionId = 1, ships = 100, supplies = 100)
        val arg = mapOf<String, Any>("destX" to 100, "destY" to 200)
        val cmd = 좌표이동(general, createTestEnv(), arg)

        val cond = cmd.checkFullCondition()
        assertTrue(cond is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(LiteHashDRBG.build(GOLDEN_SEED)) }
        assertTrue(result.success)

        // Entity mutation: destX/Y set
        assertEquals(100f, general.destX)
        assertEquals(200f, general.destY)

        // Log generated
        assertTrue(result.logs[0].contains("(100, 200)"))
        assertTrue(result.logs[0].contains("이동"))
    }

    @Test
    fun `kotlin-only 좌표이동 fails with invalid coordinates`() {
        val general = createTestGeneral(factionId = 1, ships = 100, supplies = 100)
        val arg = mapOf<String, Any>("destX" to 800, "destY" to 200)
        val cmd = 좌표이동(general, createTestEnv(), arg)

        val result = runBlocking { cmd.run(LiteHashDRBG.build(GOLDEN_SEED)) }
        assertFalse(result.success)
    }
}
