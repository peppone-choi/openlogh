package com.openlogh.command

import com.openlogh.command.constraint.ConstraintResult
import com.openlogh.command.nation.che_선전포고
import com.openlogh.command.nation.che_급습
import com.openlogh.command.nation.che_불가침수락
import com.openlogh.command.nation.che_불가침제의
import com.openlogh.command.nation.che_불가침파기수락
import com.openlogh.command.nation.che_불가침파기제의
import com.openlogh.command.nation.che_수몰
import com.openlogh.command.nation.che_의병모집
import com.openlogh.command.nation.che_이호경식
import com.openlogh.command.nation.che_종전수락
import com.openlogh.command.nation.che_종전제의
import com.openlogh.command.nation.che_피장파장
import com.openlogh.command.nation.che_허보
import com.openlogh.engine.DiplomacyService
import com.openlogh.entity.Planet
import com.openlogh.entity.Diplomacy
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
import kotlin.random.Random

class NationDiplomacyStrategicCommandTest {

    private val fixedRng = Random(42)

    private fun createGeneral(
        id: Long = 1,
        nationId: Long = 1,
        cityId: Long = 1,
        officerLevel: Short = 20,
        gold: Int = 1000,
        rice: Int = 1000,
    ): Officer = Officer(
        id = id,
        sessionId = 1,
        name = "테스트장수$id",
        factionId = nationId,
        planetId = cityId,
        officerLevel = officerLevel,
        funds = gold,
        supplies = rice,
        leadership = 50,
        command = 50,
        intelligence = 50,
        politics = 50,
        administration = 50,
        turnTime = OffsetDateTime.now(),
    )

    private fun createCity(
        id: Long = 1,
        nationId: Long = 1,
        supplyState: Short = 1,
        def: Int = 500,
        wall: Int = 500,
        pop: Int = 10000,
    ): Planet = Planet(
        id = id,
        sessionId = 1,
        name = "테스트도시$id",
        factionId = nationId,
        supplyState = supplyState,
        production = 500,
        productionMax = 1000,
        commerce = 500,
        commerceMax = 1000,
        security = 500,
        securityMax = 1000,
        orbitalDefense = def,
        orbitalDefenseMax = 1000,
        fortress = wall,
        fortressMax = 1000,
        population = pop,
        populationMax = 50000,
        approval = 80,
    )

    private fun createNation(
        id: Long = 1,
        level: Short = 7,
        strategicCmdLimit: Short = 0,
        gold: Int = 200000,
        rice: Int = 200000,
    ): Faction = Faction(
        id = id,
        sessionId = 1,
        name = "테스트국가$id",
        color = "#FF0000",
        funds = gold,
        supplies = rice,
        factionRank = level,
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

    private data class MockServicesBundle(
        val services: CommandServices,
        val officerRepository: OfficerRepository,
        val planetRepository: PlanetRepository,
        val factionRepository: FactionRepository,
        val diplomacyService: DiplomacyService,
    )

    private fun createMockServicesBundle(): MockServicesBundle {
        val officerRepository = mock(OfficerRepository::class.java)
        val planetRepository = mock(PlanetRepository::class.java)
        val factionRepository = mock(FactionRepository::class.java)
        val diplomacyService = mock(DiplomacyService::class.java)

        return MockServicesBundle(
            services = CommandServices(
                officerRepository = officerRepository,
                planetRepository = planetRepository,
                factionRepository = factionRepository,
                diplomacyService = diplomacyService,
            ),
            officerRepository = officerRepository,
            planetRepository = planetRepository,
            factionRepository = factionRepository,
            diplomacyService = diplomacyService,
        )
    }

    @Test
    fun `종전제의 checks chief and run calls diplomacy service`() {
        val nonChief = che_종전제의(createGeneral(officerLevel = 5), env())
        nonChief.city = createCity()
        nonChief.nation = createNation(id = 1)
        nonChief.destFaction = createNation(id = 2)
        val failed = nonChief.checkFullCondition()
        assertTrue(failed is ConstraintResult.Fail)

        val general = createGeneral(officerLevel = 20)
        val cmd = che_종전제의(general, env())
        val mocks = createMockServicesBundle()
        cmd.services = mocks.services
        cmd.city = createCity(nationId = 1, supplyState = 1)
        cmd.nation = createNation(id = 1)
        cmd.destFaction = createNation(id = 2)
        cmd.constraintEnv = mapOf("diplomacy_1_2" to 0) // at war

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        verify(mocks.diplomacyService).proposeCeasefire(1, 1, 2)
    }

    @Test
    fun `종전수락 checks destination general and run calls diplomacy service`() {
        val cmd1 = che_종전수락(createGeneral(), env())
        cmd1.nation = createNation(id = 1)
        cmd1.destFaction = createNation(id = 2)
        val failed = cmd1.checkFullCondition()
        assertTrue(failed is ConstraintResult.Fail)

        val general = createGeneral(officerLevel = 20)
        val cmd = che_종전수락(general, env())
        val mocks = createMockServicesBundle()
        cmd.services = mocks.services
        cmd.nation = createNation(id = 1)
        cmd.destFaction = createNation(id = 2)
        cmd.destOfficer = createGeneral(id = 10, nationId = 2)
        cmd.constraintEnv = mapOf("diplomacy_1_2" to 0) // at war

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        verify(mocks.diplomacyService).acceptCeasefire(1, 1, 2)
    }

    @Test
    fun `불가침제의 checks destination nation and run calls diplomacy service`() {
        val cmd1 = che_불가침제의(createGeneral(), env())
        cmd1.nation = createNation(id = 1)
        val failed = cmd1.checkFullCondition()
        assertTrue(failed is ConstraintResult.Fail)

        val general = createGeneral(officerLevel = 20)
        val cmd = che_불가침제의(general, env())
        val mocks = createMockServicesBundle()
        cmd.services = mocks.services
        cmd.nation = createNation(id = 1)
        cmd.destFaction = createNation(id = 2)
        cmd.constraintEnv = mapOf("diplomacy_1_2" to 2) // peace (not at war)

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        verify(mocks.diplomacyService).proposeNonAggression(1, 1, 2)
    }

    @Test
    fun `불가침수락 checks occupation and run calls diplomacy service`() {
        val cmd1 = che_불가침수락(createGeneral(), env())
        cmd1.city = createCity(nationId = 2)
        cmd1.nation = createNation(id = 1)
        cmd1.destFaction = createNation(id = 2)
        cmd1.destOfficer = createGeneral(id = 10, nationId = 2)
        val failed = cmd1.checkFullCondition()
        assertTrue(failed is ConstraintResult.Fail)

        val general = createGeneral(officerLevel = 20)
        val cmd = che_불가침수락(general, env())
        val mocks = createMockServicesBundle()
        cmd.services = mocks.services
        cmd.city = createCity(nationId = 1, supplyState = 1)
        cmd.nation = createNation(id = 1)
        cmd.destFaction = createNation(id = 2)
        cmd.destOfficer = createGeneral(id = 10, nationId = 2)
        cmd.constraintEnv = mapOf("diplomacy_1_2" to 2) // peace (not at war)

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        verify(mocks.diplomacyService).acceptNonAggression(1, 1, 2)
    }

    @Test
    fun `불가침파기제의 checks chief and run calls diplomacy service`() {
        val nonChief = che_불가침파기제의(createGeneral(officerLevel = 5), env())
        nonChief.city = createCity()
        nonChief.nation = createNation(id = 1)
        nonChief.destFaction = createNation(id = 2)
        val failed = nonChief.checkFullCondition()
        assertTrue(failed is ConstraintResult.Fail)

        val general = createGeneral(officerLevel = 20)
        val cmd = che_불가침파기제의(general, env())
        val mocks = createMockServicesBundle()
        cmd.services = mocks.services
        cmd.city = createCity(nationId = 1, supplyState = 1)
        cmd.nation = createNation(id = 1)
        cmd.destFaction = createNation(id = 2)
        cmd.constraintEnv = mapOf("diplomacy_1_2" to 7) // non-aggression pact

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        verify(mocks.diplomacyService).proposeBreakNonAggression(1, 1, 2)
    }

    @Test
    fun `불가침파기수락 checks destination general and run calls diplomacy service`() {
        val cmd1 = che_불가침파기수락(createGeneral(), env())
        cmd1.nation = createNation(id = 1)
        cmd1.destFaction = createNation(id = 2)
        val failed = cmd1.checkFullCondition()
        assertTrue(failed is ConstraintResult.Fail)

        val general = createGeneral(officerLevel = 20)
        val cmd = che_불가침파기수락(general, env())
        val mocks = createMockServicesBundle()
        cmd.services = mocks.services
        cmd.nation = createNation(id = 1)
        cmd.destFaction = createNation(id = 2)
        cmd.destOfficer = createGeneral(id = 10, nationId = 2)
        cmd.constraintEnv = mapOf("diplomacy_1_2" to 7) // non-aggression pact

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        verify(mocks.diplomacyService).acceptBreakNonAggression(1, 1, 2)
    }

    @Test
    fun `급습 validates strategic metadata constraint and run`() {
        val cmdMeta = che_급습(createGeneral(), env())
        assertEquals(0, cmdMeta.getCost().funds)
        assertEquals(0, cmdMeta.getCost().supplies)
        assertEquals(0, cmdMeta.getPreReqTurn())
        assertEquals(40, cmdMeta.getPostReqTurn())

        val nonChief = che_급습(createGeneral(officerLevel = 5), env())
        nonChief.city = createCity(nationId = 1)
        nonChief.nation = createNation(id = 1)
        nonChief.destFaction = createNation(id = 2)
        val failed = nonChief.checkFullCondition()
        assertTrue(failed is ConstraintResult.Fail)

        val general = createGeneral(officerLevel = 20)
        val cmd = che_급습(general, env())
        val mocks = createMockServicesBundle()
        val relations = listOf(
            Diplomacy(sessionId = 1, srcFactionId = 2, destFactionId = 3, stateCode = "불가침", term = 7),
            Diplomacy(sessionId = 1, srcFactionId = 2, destFactionId = 4, stateCode = "선전포고", term = 0),
        )
        `when`(mocks.diplomacyService.getRelationsForNation(1, 2)).thenReturn(relations)

        val nation = createNation(id = 1)
        cmd.services = mocks.services
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.destFaction = createNation(id = 2)

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(9, nation.strategicCmdLimit.toInt())
        assertEquals(4, relations[0].term.toInt())
        assertEquals(0, relations[1].term.toInt())
        assertTrue(result.logs.any { it.contains("급습") })
    }

    @Test
    fun `수몰 validates strategic metadata constraint and run`() {
        val cmdMeta = che_수몰(createGeneral(), env())
        assertEquals(0, cmdMeta.getCost().funds)
        assertEquals(0, cmdMeta.getCost().supplies)
        assertEquals(2, cmdMeta.getPreReqTurn())
        assertEquals(20, cmdMeta.getPostReqTurn())

        val cmdFail = che_수몰(createGeneral(officerLevel = 20), env())
        cmdFail.city = createCity(nationId = 1)
        cmdFail.nation = createNation(id = 1)
        cmdFail.destPlanet = createCity(id = 2, nationId = 2)
        cmdFail.constraintEnv = mapOf("atWarNationIds" to emptySet<Long>())
        val failed = cmdFail.checkFullCondition()
        assertTrue(failed is ConstraintResult.Fail)

        val general = createGeneral(officerLevel = 20)
        val cmd = che_수몰(general, env())
        val nation = createNation(id = 1)
        val destPlanet = createCity(id = 2, nationId = 2, def = 800, wall = 600, pop = 10000)
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.destPlanet = destPlanet
        cmd.constraintEnv = mapOf("atWarNationIds" to setOf(2L))

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(9, nation.strategicCmdLimit.toInt())
        assertEquals(160, destPlanet.orbitalDefense)
        assertEquals(120, destPlanet.fortress)
        assertEquals(5000, destPlanet.population)
        assertEquals(500, destPlanet.dead)
    }

    @Test
    fun `허보 validates strategic metadata constraint and run`() {
        val cmdMeta = che_허보(createGeneral(), env())
        assertEquals(0, cmdMeta.getCost().funds)
        assertEquals(0, cmdMeta.getCost().supplies)
        assertEquals(1, cmdMeta.getPreReqTurn())
        assertEquals(20, cmdMeta.getPostReqTurn())

        val cmdFail = che_허보(createGeneral(), env())
        cmdFail.city = createCity(nationId = 1)
        cmdFail.nation = createNation(id = 1)
        val failed = cmdFail.checkFullCondition()
        assertTrue(failed is ConstraintResult.Fail)

        val general = createGeneral(officerLevel = 20)
        val cmd = che_허보(general, env())
        val nation = createNation(id = 1)
        val destPlanet = createCity(id = 2, nationId = 2)
        val enemyGeneral1 = createGeneral(id = 101, nationId = 2, cityId = 2).apply { troopId = 999 }
        val enemyGeneral2 = createGeneral(id = 102, nationId = 2, cityId = 2).apply { troopId = 102 }
        val allyGeneral = createGeneral(id = 201, nationId = 1, cityId = 2)

        val mocks = createMockServicesBundle()
        `when`(mocks.officerRepository.findByPlanetId(2)).thenReturn(listOf(enemyGeneral1, enemyGeneral2, allyGeneral))
        `when`(mocks.planetRepository.findByFactionId(2)).thenReturn(listOf(destPlanet, createCity(id = 3, nationId = 2)))

        cmd.services = mocks.services
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.destPlanet = destPlanet

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(9, nation.strategicCmdLimit.toInt())
        assertEquals(3L, enemyGeneral1.planetId)
        assertEquals(3L, enemyGeneral2.planetId)
        assertEquals(0L, enemyGeneral1.fleetId)
        assertEquals(102L, enemyGeneral2.fleetId)
        verify(mocks.officerRepository, times(2)).save(org.mockito.Mockito.any(General::class.java))
    }

    @Test
    fun `의병모집 validates strategic metadata constraint and run`() {
        val cmdMeta = che_의병모집(createGeneral(), env())
        assertEquals(0, cmdMeta.getCost().funds)
        assertEquals(0, cmdMeta.getCost().supplies)
        assertEquals(2, cmdMeta.getPreReqTurn())
        assertEquals(100, cmdMeta.getPostReqTurn())

        val cmdFail = che_의병모집(createGeneral(), env(year = 190, startYear = 190))
        cmdFail.city = createCity(nationId = 1)
        cmdFail.nation = createNation(id = 1)
        val failed = cmdFail.checkFullCondition()
        assertTrue(failed is ConstraintResult.Fail)

        val cmd = che_의병모집(createGeneral(officerLevel = 20), env(year = 200, startYear = 190))
        val nation = createNation(id = 1)
        val city = createCity(id = 1, nationId = 1, pop = 30000)
        val mocks = createMockServicesBundle()

        cmd.services = mocks.services
        cmd.city = city
        cmd.nation = nation

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(15000, city.population)
        assertEquals(9, nation.strategicCmdLimit.toInt())
        verify(mocks.officerRepository, times(3)).save(org.mockito.Mockito.any(General::class.java))
    }

    @Test
    fun `이호경식 validates strategic metadata constraint and run`() {
        val cmdMeta = che_이호경식(createGeneral(), env())
        assertEquals(0, cmdMeta.getCost().funds)
        assertEquals(0, cmdMeta.getCost().supplies)
        assertEquals(0, cmdMeta.getPreReqTurn())
        assertEquals(126, cmdMeta.getPostReqTurn())

        val cmdFail = che_이호경식(createGeneral(), env())
        cmdFail.city = createCity(nationId = 1)
        cmdFail.nation = createNation(id = 1)
        val failed = cmdFail.checkFullCondition()
        assertTrue(failed is ConstraintResult.Fail)

        val cmd = che_이호경식(createGeneral(officerLevel = 20), env())
        val nation = createNation(id = 1)
        val destFaction = createNation(id = 2)
        val mocks = createMockServicesBundle()
        // PHP: sets diplomacy state=1 (declared), term = if(state==0) 3 else term+3
        `when`(mocks.diplomacyService.getDiplomacyState(1, 1, 2)).thenReturn(null)

        cmd.services = mocks.services
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.destFaction = destFaction

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(9, nation.strategicCmdLimit.toInt())
        verify(mocks.diplomacyService).setDiplomacyState(1, 1, 2, state = 1, term = 3)
    }

    @Test
    fun `피장파장 validates strategic metadata constraint and run`() {
        val cmdMeta = che_피장파장(createGeneral(), env())
        assertEquals(0, cmdMeta.getCost().funds)
        assertEquals(0, cmdMeta.getCost().supplies)
        assertEquals(1, cmdMeta.getPreReqTurn())
        assertEquals(8, cmdMeta.getPostReqTurn())

        val cmdFail = che_피장파장(createGeneral(), env())
        cmdFail.city = createCity(nationId = 1)
        cmdFail.nation = createNation(id = 1, strategicCmdLimit = 2)
        cmdFail.destFaction = createNation(id = 2)
        val failed = cmdFail.checkFullCondition()
        assertTrue(failed is ConstraintResult.Fail)

        val cmd = che_피장파장(createGeneral(officerLevel = 20), env())
        val nation = createNation(id = 1, strategicCmdLimit = 0)
        val destFaction = createNation(id = 2, strategicCmdLimit = 5)
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.destFaction = destFaction

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertEquals(9, nation.strategicCmdLimit.toInt())
        assertEquals(9, destFaction.strategicCmdLimit.toInt())
    }

    // ========== Golden Value: Diplomacy Command Parity (D-05, D-06) ==========

    @Test
    fun `선전포고 golden value -- diplomacy state set and color-tagged logs`() {
        val general = createGeneral(officerLevel = 20)
        val cmd = che_선전포고(general, env())
        val mocks = createMockServicesBundle()
        cmd.services = mocks.services
        cmd.city = createCity(nationId = 1, supplyState = 1)
        cmd.nation = createNation(id = 1)
        cmd.destFaction = createNation(id = 2)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: diplomacy state=1, term=24
        verify(mocks.diplomacyService).setDiplomacyState(1, 1, 2, state = 1, term = 24)
        // Color-tagged log: general action (D-05)
        assertTrue(result.logs.any { it.contains("<D><b>테스트국가2</b></>에 선전 포고") })
        // Color-tagged global history log (D-05)
        assertTrue(result.logs.any { it.contains("<R><b>【선포】</b>") })
        // Color-tagged global action log
        assertTrue(result.logs.any { it.contains("<M>선전 포고</>") })
    }

    @Test
    fun `종전제의 golden value -- diplomacy service call and color-tagged log`() {
        val general = createGeneral(officerLevel = 20)
        val cmd = che_종전제의(general, env())
        val mocks = createMockServicesBundle()
        cmd.services = mocks.services
        cmd.city = createCity(nationId = 1, supplyState = 1)
        cmd.nation = createNation(id = 1)
        cmd.destFaction = createNation(id = 2)
        cmd.constraintEnv = mapOf("diplomacy_1_2" to 0)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: proposeCeasefire called
        verify(mocks.diplomacyService).proposeCeasefire(1, 1, 2)
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("종전") })
    }

    @Test
    fun `종전수락 golden value -- diplomacy service call and log`() {
        val general = createGeneral(officerLevel = 20)
        val cmd = che_종전수락(general, env())
        val mocks = createMockServicesBundle()
        cmd.services = mocks.services
        cmd.nation = createNation(id = 1)
        cmd.destFaction = createNation(id = 2)
        cmd.destOfficer = createGeneral(id = 10, nationId = 2)
        cmd.constraintEnv = mapOf("diplomacy_1_2" to 0)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: acceptCeasefire called
        verify(mocks.diplomacyService).acceptCeasefire(1, 1, 2)
        assertTrue(result.logs.any { it.contains("종전") })
    }

    @Test
    fun `불가침제의 golden value -- diplomacy service call and log`() {
        val general = createGeneral(officerLevel = 20)
        val cmd = che_불가침제의(general, env())
        val mocks = createMockServicesBundle()
        cmd.services = mocks.services
        cmd.nation = createNation(id = 1)
        cmd.destFaction = createNation(id = 2)
        cmd.constraintEnv = mapOf("diplomacy_1_2" to 2)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        verify(mocks.diplomacyService).proposeNonAggression(1, 1, 2)
        assertTrue(result.logs.any { it.contains("불가침") })
    }

    @Test
    fun `불가침수락 golden value -- diplomacy service call and log`() {
        val general = createGeneral(officerLevel = 20)
        val cmd = che_불가침수락(general, env())
        val mocks = createMockServicesBundle()
        cmd.services = mocks.services
        cmd.city = createCity(nationId = 1, supplyState = 1)
        cmd.nation = createNation(id = 1)
        cmd.destFaction = createNation(id = 2)
        cmd.destOfficer = createGeneral(id = 10, nationId = 2)
        cmd.constraintEnv = mapOf("diplomacy_1_2" to 2)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        verify(mocks.diplomacyService).acceptNonAggression(1, 1, 2)
        assertTrue(result.logs.any { it.contains("불가침") })
    }

    @Test
    fun `불가침파기제의 golden value -- diplomacy service call and log`() {
        val general = createGeneral(officerLevel = 20)
        val cmd = che_불가침파기제의(general, env())
        val mocks = createMockServicesBundle()
        cmd.services = mocks.services
        cmd.city = createCity(nationId = 1, supplyState = 1)
        cmd.nation = createNation(id = 1)
        cmd.destFaction = createNation(id = 2)
        cmd.constraintEnv = mapOf("diplomacy_1_2" to 7)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        verify(mocks.diplomacyService).proposeBreakNonAggression(1, 1, 2)
        assertTrue(result.logs.any { it.contains("불가침") })
    }

    @Test
    fun `불가침파기수락 golden value -- diplomacy service call and log`() {
        val general = createGeneral(officerLevel = 20)
        val cmd = che_불가침파기수락(general, env())
        val mocks = createMockServicesBundle()
        cmd.services = mocks.services
        cmd.nation = createNation(id = 1)
        cmd.destFaction = createNation(id = 2)
        cmd.destOfficer = createGeneral(id = 10, nationId = 2)
        cmd.constraintEnv = mapOf("diplomacy_1_2" to 7)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        verify(mocks.diplomacyService).acceptBreakNonAggression(1, 1, 2)
        assertTrue(result.logs.any { it.contains("불가침") })
    }

    // ========== Golden Value: Strategic Command Parity (D-05, D-06) ==========

    @Test
    fun `급습 golden value -- relation term reduced and exp gained`() {
        val general = createGeneral(officerLevel = 20)
        general.experience = 0
        general.dedication = 0
        val cmd = che_급습(general, env())
        val mocks = createMockServicesBundle()
        val relations = listOf(
            Diplomacy(sessionId = 1, srcFactionId = 2, destFactionId = 3, stateCode = "불가침", term = 10),
            Diplomacy(sessionId = 1, srcFactionId = 2, destFactionId = 4, stateCode = "선전포고", term = 5),
        )
        `when`(mocks.diplomacyService.getRelationsForNation(1, 2)).thenReturn(relations)

        val nation = createNation(id = 1)
        cmd.services = mocks.services
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.destFaction = createNation(id = 2)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: strategicCmdLimit = 9
        assertEquals(9, nation.strategicCmdLimit.toInt())
        // PHP golden value: 불가침 term reduced by 3 (TERM_REDUCE)
        assertEquals(7, relations[0].term.toInt(), "불가침 term = 10 - 3")
        // PHP golden value: non-불가침 relations unchanged
        assertEquals(5, relations[1].term.toInt(), "선전포고 term unchanged")
        // PHP golden value: exp/ded += 50
        assertEquals(50, general.experience, "experience += 50")
        assertEquals(50, general.dedication, "dedication += 50")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("급습 발동") })
        assertTrue(result.logs.any { it.contains("<Y>테스트장수1</>") })
    }

    @Test
    fun `수몰 golden value -- dest city damage exact values`() {
        val general = createGeneral(officerLevel = 20)
        general.experience = 0
        general.dedication = 0
        val cmd = che_수몰(general, env())
        val nation = createNation(id = 1)
        val destPlanet = createCity(id = 2, nationId = 2, def = 1000, wall = 500, pop = 20000)
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.destPlanet = destPlanet
        cmd.constraintEnv = mapOf("atWarNationIds" to setOf(2L))

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: def *= 0.2, wall *= 0.2, pop *= 0.5
        assertEquals(200, destPlanet.orbitalDefense, "def = 1000 * 0.2")
        assertEquals(100, destPlanet.fortress, "wall = 500 * 0.2")
        assertEquals(10000, destPlanet.population, "pop = 20000 * 0.5")
        // PHP: dead += (beforePop - afterPop) * 0.1 = (20000 - 10000) * 0.1 = 1000
        assertEquals(1000, destPlanet.dead, "dead = 10000 * 0.1")
        // PHP: strategicCmdLimit = 9
        assertEquals(9, nation.strategicCmdLimit.toInt())
        // PHP: exp/ded = 5 * (2 + 1) = 15
        assertEquals(15, general.experience, "experience = 5 * 3")
        assertEquals(15, general.dedication, "dedication = 5 * 3")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("<M>수몰</>") })
    }

    @Test
    fun `허보 golden value -- enemy generals relocated and troop leaders reset`() {
        val general = createGeneral(officerLevel = 20)
        val cmd = che_허보(general, env())
        val nation = createNation(id = 1)
        val destPlanet = createCity(id = 2, nationId = 2)
        val enemy1 = createGeneral(id = 101, nationId = 2, cityId = 2).apply { troopId = 999 }
        val enemy2 = createGeneral(id = 102, nationId = 2, cityId = 2).apply { troopId = 102 }
        val otherCity = createCity(id = 3, nationId = 2)

        val mocks = createMockServicesBundle()
        `when`(mocks.officerRepository.findByPlanetId(2)).thenReturn(listOf(enemy1, enemy2))
        `when`(mocks.planetRepository.findByFactionId(2)).thenReturn(listOf(destPlanet, otherCity))

        cmd.services = mocks.services
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.destPlanet = destPlanet

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: enemy generals moved to random other city
        assertEquals(3L, enemy1.planetId, "enemy1 relocated to city 3")
        assertEquals(3L, enemy2.planetId, "enemy2 relocated to city 3")
        // PHP golden value: troop leader (troopId != self.id) reset to 0
        assertEquals(0L, enemy1.fleetId, "troop leader enemy1 troopId reset")
        // PHP golden value: troop member (troopId == self.id) kept
        assertEquals(102L, enemy2.fleetId, "troop member enemy2 troopId kept")
        assertEquals(9, nation.strategicCmdLimit.toInt())
    }

    @Test
    fun `의병모집 golden value -- pop halved and NPC generals created`() {
        val general = createGeneral(officerLevel = 20)
        general.experience = 0
        general.dedication = 0
        val cmd = che_의병모집(general, env(year = 200, startYear = 190))
        val nation = createNation(id = 1)
        val city = createCity(id = 1, nationId = 1, pop = 40000)
        val mocks = createMockServicesBundle()

        cmd.services = mocks.services
        cmd.city = city
        cmd.nation = nation

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: pop halved
        assertEquals(20000, city.population, "pop = 40000 * 0.5")
        // PHP golden value: strategicCmdLimit = 9
        assertEquals(9, nation.strategicCmdLimit.toInt())
        // PHP golden value: exp/ded = 5 * (2 + 1) = 15
        assertEquals(15, general.experience, "experience = 5 * 3")
        assertEquals(15, general.dedication, "dedication = 5 * 3")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("<M>의병모집</>") })
    }

    @Test
    fun `이호경식 golden value -- diplomacy state set and exp gained`() {
        val general = createGeneral(officerLevel = 20)
        general.experience = 0
        general.dedication = 0
        val cmd = che_이호경식(general, env())
        val nation = createNation(id = 1)
        val destFaction = createNation(id = 2)
        val mocks = createMockServicesBundle()
        `when`(mocks.diplomacyService.getDiplomacyState(1, 1, 2)).thenReturn(null)

        cmd.services = mocks.services
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.destFaction = destFaction

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: strategicCmdLimit = 9
        assertEquals(9, nation.strategicCmdLimit.toInt())
        // PHP golden value: diplomacy state=1, term=3 (new relation)
        verify(mocks.diplomacyService).setDiplomacyState(1, 1, 2, state = 1, term = 3)
    }

    @Test
    fun `피장파장 golden value -- both nations strategic cmd limited`() {
        val general = createGeneral(officerLevel = 20)
        general.experience = 0
        general.dedication = 0
        val cmd = che_피장파장(general, env())
        val nation = createNation(id = 1, strategicCmdLimit = 0)
        val destFaction = createNation(id = 2, strategicCmdLimit = 3)
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.destFaction = destFaction

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: both nations get strategicCmdLimit = 9
        assertEquals(9, nation.strategicCmdLimit.toInt(), "src nation limit = 9")
        assertEquals(9, destFaction.strategicCmdLimit.toInt(), "dest nation limit = 9")
        // PHP golden value: exp/ded = 5 * (1 + 1) = 10
        assertEquals(10, general.experience, "experience = 5 * 2")
        assertEquals(10, general.dedication, "dedication = 5 * 2")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("피장파장") })
    }

    @Test
    fun `피장파장 golden value -- failure when strategic limit active`() {
        val cmdFail = che_피장파장(createGeneral(officerLevel = 20), env())
        cmdFail.city = createCity(nationId = 1)
        cmdFail.nation = createNation(id = 1, strategicCmdLimit = 5)
        cmdFail.destFaction = createNation(id = 2)
        val failed = cmdFail.checkFullCondition()
        assertTrue(failed is ConstraintResult.Fail)
        assertTrue((failed as ConstraintResult.Fail).reason.contains("전략 명령 대기"))
    }
}
