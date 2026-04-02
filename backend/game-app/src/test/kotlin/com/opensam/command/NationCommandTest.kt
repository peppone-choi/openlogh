package com.opensam.command

import com.opensam.command.constraint.ConstraintResult
import com.opensam.command.nation.che_선전포고
import com.opensam.command.nation.che_선양요구
import com.opensam.command.nation.che_신속
import com.opensam.command.nation.che_칭제
import com.opensam.command.nation.che_천자맞이
import com.opensam.command.nation.che_독립선언
import com.opensam.command.nation.che_포상
import com.opensam.command.nation.che_초토화
import com.opensam.command.nation.che_필사즉생
import com.opensam.command.nation.event_극병연구
import com.opensam.engine.DiplomacyService
import com.opensam.engine.EmperorConstants
import com.opensam.entity.City
import com.opensam.entity.General
import com.opensam.entity.Nation
import com.opensam.repository.CityRepository
import com.opensam.repository.GeneralRepository
import com.opensam.repository.NationRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.Optional
import kotlin.random.Random

class NationCommandTest {

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
    fun `선전포고 requires chief and destination nation`() {
        val nonChief = createGeneral(officerLevel = 5)
        val cmd1 = che_선전포고(nonChief, env())
        cmd1.city = createCity()
        cmd1.nation = createNation()
        cmd1.destNation = createNation(id = 2)
        val r1 = cmd1.checkFullCondition()
        assertTrue(r1 is ConstraintResult.Fail)
        assertTrue((r1 as ConstraintResult.Fail).reason.contains("군주"))

        val chief = createGeneral(officerLevel = 20)
        val cmd2 = che_선전포고(chief, env())
        cmd2.city = createCity()
        cmd2.nation = createNation()
        val r2 = cmd2.checkFullCondition()
        assertTrue(r2 is ConstraintResult.Fail)
        assertTrue((r2 as ConstraintResult.Fail).reason.contains("대상 국가"))
    }

    @Test
    fun `선전포고 fails during opening period`() {
        val general = createGeneral(officerLevel = 20)
        val cmd = che_선전포고(general, env(year = 191, startYear = 191))
        cmd.city = createCity()
        cmd.nation = createNation(id = 1)
        cmd.destNation = createNation(id = 2)

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Fail)
        assertTrue((check as ConstraintResult.Fail).reason.contains("오프닝"))
    }

    @Test
    fun `포상 requires friendly destination general`() {
        val chief = createGeneral(officerLevel = 20, nationId = 1)
        val cmd = che_포상(chief, env(), mapOf("isGold" to true, "amount" to 1000))
        cmd.city = createCity(nationId = 1)
        cmd.nation = createNation(id = 1)

        val withoutDest = cmd.checkFullCondition()
        assertTrue(withoutDest is ConstraintResult.Fail)
        assertTrue((withoutDest as ConstraintResult.Fail).reason.contains("대상 장수"))

        cmd.destGeneral = createGeneral(id = 2, nationId = 2)
        val enemyDest = cmd.checkFullCondition()
        assertTrue(enemyDest is ConstraintResult.Fail)
        assertTrue((enemyDest as ConstraintResult.Fail).reason.contains("아군"))
    }

    @Test
    fun `포상 runs and writes reward log`() {
        val chief = createGeneral(officerLevel = 20, nationId = 1)
        val cmd = che_포상(chief, env(), mapOf("isGold" to false, "amount" to 1234))
        cmd.city = createCity(nationId = 1)
        cmd.nation = createNation(id = 1)
        cmd.destGeneral = createGeneral(id = 2, nationId = 1)

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.logs.any { it.contains("수여") })
        assertTrue(result.logs.any { it.contains("쌀") })
    }

    @Test
    fun `초토화 has expected pre and post turn requirement`() {
        val cmd = che_초토화(createGeneral(), env())
        assertEquals(2, cmd.getPreReqTurn())
        assertEquals(24, cmd.getPostReqTurn())
    }

    @Test
    fun `초토화 requires occupied destination city and chief`() {
        val nonChief = createGeneral(officerLevel = 5, nationId = 1)
        val cmd1 = che_초토화(nonChief, env())
        cmd1.city = createCity(nationId = 1)
        cmd1.destCity = createCity(id = 2, nationId = 1)
        val r1 = cmd1.checkFullCondition()
        assertTrue(r1 is ConstraintResult.Fail)
        assertTrue((r1 as ConstraintResult.Fail).reason.contains("군주"))

        val chief = createGeneral(officerLevel = 20, nationId = 1)
        val cmd2 = che_초토화(chief, env())
        cmd2.city = createCity(nationId = 1)
        cmd2.destCity = createCity(id = 2, nationId = 2)
        val r2 = cmd2.checkFullCondition()
        assertTrue(r2 is ConstraintResult.Fail)
        assertTrue((r2 as ConstraintResult.Fail).reason.contains("아군 도시"))
    }

    @Test
    fun `초토화 runs and emits action log`() {
        val chief = createGeneral(officerLevel = 20, nationId = 1)
        val cmd = che_초토화(chief, env())
        cmd.city = createCity(nationId = 1)
        cmd.destCity = createCity(id = 2, nationId = 1)
        cmd.nation = createNation(id = 1)

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.logs.any { it.contains("초토화") })
    }

    @Test
    fun `극병연구 requires nation resource threshold and has expected cost`() {
        val chief = createGeneral(officerLevel = 20)

        val lowGoldNation = createNation(gold = 100000, rice = 200000)
        val cmd1 = event_극병연구(chief, env())
        cmd1.city = createCity()
        cmd1.nation = lowGoldNation
        val r1 = cmd1.checkFullCondition()
        assertTrue(r1 is ConstraintResult.Fail)
        assertTrue((r1 as ConstraintResult.Fail).reason.contains("국고"))

        val lowRiceNation = createNation(gold = 200000, rice = 100000)
        val cmd2 = event_극병연구(chief, env())
        cmd2.city = createCity()
        cmd2.nation = lowRiceNation
        val r2 = cmd2.checkFullCondition()
        assertTrue(r2 is ConstraintResult.Fail)
        assertTrue((r2 as ConstraintResult.Fail).reason.contains("병량"))

        val cmd3 = event_극병연구(chief, env())
        assertEquals(23, cmd3.getPreReqTurn())
        assertEquals(100000, cmd3.getCost().gold)
        assertEquals(100000, cmd3.getCost().rice)
    }

    @Test
    fun `극병연구 runs and emits completion log`() {
        val chief = createGeneral(officerLevel = 20)
        val cmd = event_극병연구(chief, env())
        cmd.city = createCity()
        cmd.nation = createNation(gold = 200000, rice = 200000)

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        assertTrue(result.logs.any { it.contains("극병 연구") })
    }

    @Test
    fun `nation strategic command fails when strategic limit is active`() {
        val chief = createGeneral(officerLevel = 20)
        val cmd = che_필사즉생(chief, env())
        cmd.city = createCity()
        cmd.nation = createNation(strategicCmdLimit = 5)

        val result = cmd.checkFullCondition()
        assertTrue(result is ConstraintResult.Fail)
        assertTrue((result as ConstraintResult.Fail).reason.contains("전략 명령 대기"))
    }

    // ========== Golden Value: 선전포고 color-tagged log (D-05) ==========

    @Test
    fun `선전포고 golden value -- color-tagged action and history logs`() {
        val general = createGeneral(officerLevel = 20)
        val mocks = createMockServicesBundle()
        val cmd = che_선전포고(general, env())
        cmd.services = mocks.services
        cmd.city = createCity(nationId = 1, supplyState = 1)
        cmd.nation = createNation(id = 1)
        cmd.destNation = createNation(id = 2)

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: diplomacy state=1, term=24
        verify(mocks.diplomacyService).setDiplomacyState(1, 1, 2, state = 1, term = 24)
        // Color-tagged log assertions (D-05)
        assertTrue(result.logs.any { it.contains("<D><b>테스트국가2</b></>에 선전 포고") }, "action log with color tag")
        assertTrue(result.logs.any { it.contains("<R><b>【선포】</b>") }, "global history with 선포 tag")
        assertTrue(result.logs.any { it.contains("<M>선전 포고</>") }, "global action with magenta tag")
    }

    // ========== Golden Value: 초토화 entity diff (D-06) ==========

    @Test
    fun `초토화 golden value -- city resources reduced to exact values`() {
        val chief = createGeneral(officerLevel = 20, nationId = 1)
        chief.experience = 100
        chief.dedication = 100
        val nation = createNation(id = 1)
        nation.capitalCityId = 99 // not the dest city

        val generalRepository = mock(com.opensam.repository.GeneralRepository::class.java)
        `when`(generalRepository.findByNationId(1L)).thenReturn(emptyList())
        val mockServices = CommandServices(
            generalRepository = generalRepository,
            cityRepository = mock(com.opensam.repository.CityRepository::class.java),
            nationRepository = mock(com.opensam.repository.NationRepository::class.java),
            diplomacyService = mock(com.opensam.engine.DiplomacyService::class.java),
        )

        val destCity = createCity(id = 2, nationId = 1)
        destCity.pop = 10000
        destCity.agri = 500
        destCity.agriMax = 1000
        destCity.comm = 500
        destCity.commMax = 1000
        destCity.secu = 500
        destCity.secuMax = 1000
        destCity.def = 500
        destCity.defMax = 1000
        destCity.wall = 500
        destCity.wallMax = 1000
        destCity.trust = 80f

        val cmd = che_초토화(chief, env())
        cmd.city = createCity(nationId = 1)
        cmd.destCity = destCity
        cmd.nation = nation
        cmd.services = mockServices

        val beforeNationGold = nation.gold
        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)

        // PHP golden value: city resources reduced to max(max*0.1, current*0.2)
        // pop: max(50000*0.1, 10000*0.2) = max(5000, 2000) = 5000
        assertEquals(maxOf((destCity.popMax * 0.1).toInt(), (10000 * 0.2).toInt()), destCity.pop, "pop reduced")
        // agri: max(1000*0.1, 500*0.2) = max(100, 100) = 100
        assertEquals(maxOf((destCity.agriMax * 0.1).toInt(), (500 * 0.2).toInt()), destCity.agri, "agri reduced")
        // comm: same as agri
        assertEquals(maxOf((destCity.commMax * 0.1).toInt(), (500 * 0.2).toInt()), destCity.comm, "comm reduced")
        // secu: same
        assertEquals(maxOf((destCity.secuMax * 0.1).toInt(), (500 * 0.2).toInt()), destCity.secu, "secu reduced")
        // def: same
        assertEquals(maxOf((destCity.defMax * 0.1).toInt(), (500 * 0.2).toInt()), destCity.def, "def reduced")
        // wall: max(1000*0.1, 500*0.5) = max(100, 250) = 250
        assertEquals(maxOf((destCity.wallMax * 0.1).toInt(), (500 * 0.5).toInt()), destCity.wall, "wall reduced (0.5 rate)")

        // PHP: city.nationId = 0, frontState = 0
        assertEquals(0L, destCity.nationId, "city released to neutral")
        assertEquals(0, destCity.frontState, "frontState reset")

        // PHP: trust clamped to min 50
        assertTrue(destCity.trust >= 50f, "trust clamped to at least 50")

        // PHP: nation gets gold/rice return
        assertTrue(nation.gold > beforeNationGold, "nation receives gold return")
        assertTrue(nation.rice > beforeNationGold, "nation receives rice return")

        // PHP: exp reduced then added: (100 * 0.9) + 5*(2+1) = 90 + 15 = 105
        assertEquals(105, chief.experience, "experience = floor(100*0.9) + 15")
        assertEquals(115, chief.dedication, "dedication = 100 + 15")

        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("<G><b>테스트도시2</b></>") && it.contains("초토화") })
    }

    @Test
    fun `초토화 golden value -- fails on capital city`() {
        val chief = createGeneral(officerLevel = 20, nationId = 1)
        val nation = createNation(id = 1)
        nation.capitalCityId = 2 // dest city IS capital

        val mockServices = CommandServices(
            generalRepository = mock(com.opensam.repository.GeneralRepository::class.java),
            cityRepository = mock(com.opensam.repository.CityRepository::class.java),
            nationRepository = mock(com.opensam.repository.NationRepository::class.java),
            diplomacyService = mock(com.opensam.engine.DiplomacyService::class.java),
        )

        val cmd = che_초토화(chief, env())
        cmd.city = createCity(nationId = 1)
        cmd.destCity = createCity(id = 2, nationId = 1)
        cmd.nation = nation
        cmd.services = mockServices

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(!result.success, "초토화 should fail on capital city")
    }

    // ========== Golden Value: 필사즉생 entity diff ==========

    @Test
    fun `필사즉생 golden value -- train and atmos raised to 100`() {
        val chief = createGeneral(officerLevel = 20)
        chief.experience = 0
        chief.dedication = 0
        chief.train = 50
        chief.atmos = 60

        val gen2 = createGeneral(id = 2, nationId = 1)
        gen2.train = 30
        gen2.atmos = 40

        val generalRepository = mock(com.opensam.repository.GeneralRepository::class.java)
        `when`(generalRepository.findByNationId(1L)).thenReturn(listOf(chief, gen2))
        val mockServices = CommandServices(
            generalRepository = generalRepository,
            cityRepository = mock(com.opensam.repository.CityRepository::class.java),
            nationRepository = mock(com.opensam.repository.NationRepository::class.java),
            diplomacyService = mock(com.opensam.engine.DiplomacyService::class.java),
        )

        val nation = createNation(id = 1, strategicCmdLimit = 0)
        val cmd = che_필사즉생(chief, env())
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.services = mockServices

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: strategicCmdLimit = 9
        assertEquals(9, nation.strategicCmdLimit.toInt())
        // PHP golden value: train/atmos raised to 100 for all generals
        assertEquals(100, chief.train.toInt(), "chief train = 100")
        assertEquals(100, chief.atmos.toInt(), "chief atmos = 100")
        assertEquals(100, gen2.train.toInt(), "gen2 train = 100")
        assertEquals(100, gen2.atmos.toInt(), "gen2 atmos = 100")
        // PHP golden value: exp/ded = 5 * (2 + 1) = 15
        assertEquals(15, chief.experience, "experience = 5 * 3")
        assertEquals(15, chief.dedication, "dedication = 5 * 3")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("필사즉생 발동") })
    }

    // ========== Golden Value: 극병연구 entity diff ==========

    @Test
    fun `극병연구 golden value -- nation gold rice reduced and meta set`() {
        val chief = createGeneral(officerLevel = 20)
        chief.experience = 0
        chief.dedication = 0
        val nation = createNation(gold = 300000, rice = 300000)
        val cmd = event_극병연구(chief, env())
        cmd.city = createCity()
        cmd.nation = nation

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // PHP golden value: gold -= 100000, rice -= 100000
        assertEquals(200000, nation.gold, "gold = 300000 - 100000")
        assertEquals(200000, nation.rice, "rice = 300000 - 100000")
        // PHP golden value: meta flag set
        assertEquals(1, nation.meta["can_극병사용"], "can_극병사용 = 1")
        // PHP golden value: exp/ded = 5 * (23 + 1) = 120
        assertEquals(120, chief.experience, "experience = 5 * 24")
        assertEquals(120, chief.dedication, "dedication = 5 * 24")
        // Color-tagged log (D-05)
        assertTrue(result.logs.any { it.contains("<M>극병 연구</>") })
    }

    // ========== Kotlin-only 5 Commands: Basic Operation Tests (D-07) ==========

    @Test
    fun `칭제 -- constraint passes for chief with level 8 plus and entity mutation`() {
        val chief = createGeneral(officerLevel = 20)
        val nation = createNation(id = 1)
        nation.level = 8
        nation.meta.remove(EmperorConstants.NATION_IMPERIAL_STATUS)

        val cmd = che_칭제(chief, env())
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.constraintEnv = mapOf(
            "emperorSystem" to true,
            "nationCityCount" to 20,
        )

        assertEquals(0, cmd.getPreReqTurn())
        assertEquals(12, cmd.getPostReqTurn())

        val check = cmd.checkFullCondition()
        assertTrue(check is ConstraintResult.Pass, "칭제 constraint should pass: $check")

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // Entity mutation: imperialStatus = emperor, emperorType = selfProclaimed
        assertEquals(EmperorConstants.STATUS_EMPEROR, nation.meta[EmperorConstants.NATION_IMPERIAL_STATUS])
        assertEquals(EmperorConstants.TYPE_SELF_PROCLAIMED, nation.meta[EmperorConstants.NATION_EMPEROR_TYPE])
        // Color-tagged log
        assertTrue(result.logs.any { it.contains("<C><b>황제</b></>") })
        assertTrue(result.logs.any { it.contains("자칭") })
    }

    @Test
    fun `칭제 -- fails for non-chief`() {
        val nonChief = createGeneral(officerLevel = 5)
        val cmd = che_칭제(nonChief, env())
        cmd.city = createCity(nationId = 1)
        cmd.nation = createNation(id = 1).also { it.level = 8 }
        cmd.constraintEnv = mapOf("emperorSystem" to true, "nationCityCount" to 20)
        val result = cmd.checkFullCondition()
        assertTrue(result is ConstraintResult.Fail)
    }

    @Test
    fun `천자맞이 -- entity mutation and color-tagged log`() {
        val chief = createGeneral(officerLevel = 20)
        val nation = createNation(id = 1)
        nation.meta.remove(EmperorConstants.NATION_IMPERIAL_STATUS)

        val emperorGeneral = createGeneral(id = 99, nationId = 0)
        emperorGeneral.meta[EmperorConstants.GENERAL_EMPEROR_STATUS] = EmperorConstants.EMPEROR_WANDERING

        val mocks = createMockServicesBundle()
        `when`(mocks.generalRepository.findById(99L)).thenReturn(java.util.Optional.of(emperorGeneral))

        val cmd = che_천자맞이(chief, env())
        cmd.services = mocks.services
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.constraintEnv = mapOf(
            "emperorSystem" to true,
            "wanderingEmperorGeneralId" to 99L,
            "wanderingEmperorInTerritory" to true,
        )

        assertEquals(0, cmd.getPreReqTurn())
        assertEquals(12, cmd.getPostReqTurn())

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // Entity mutation: nation becomes regent
        assertEquals(EmperorConstants.STATUS_REGENT, nation.meta[EmperorConstants.NATION_IMPERIAL_STATUS])
        assertEquals(EmperorConstants.TYPE_LEGITIMATE, nation.meta[EmperorConstants.NATION_EMPEROR_TYPE])
        // Emperor general: assigned to nation, enthroned
        assertEquals(1L, emperorGeneral.nationId, "emperor general joins nation")
        assertEquals(EmperorConstants.EMPEROR_ENTHRONED, emperorGeneral.meta[EmperorConstants.GENERAL_EMPEROR_STATUS])
        // Color-tagged log
        assertTrue(result.logs.any { it.contains("옹립") })
    }

    @Test
    fun `선양요구 -- entity mutation and color-tagged log`() {
        val chief = createGeneral(officerLevel = 20)
        val nation = createNation(id = 1)
        nation.level = 8
        nation.meta[EmperorConstants.NATION_IMPERIAL_STATUS] = EmperorConstants.STATUS_REGENT

        val emperorGeneral = createGeneral(id = 88, nationId = 1)
        emperorGeneral.meta[EmperorConstants.GENERAL_EMPEROR_STATUS] = EmperorConstants.EMPEROR_ENTHRONED

        val mocks = createMockServicesBundle()
        `when`(mocks.generalRepository.findById(88L)).thenReturn(java.util.Optional.of(emperorGeneral))

        val cmd = che_선양요구(chief, env())
        cmd.services = mocks.services
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.constraintEnv = mapOf(
            "emperorSystem" to true,
            "nationCityCount" to 20,
            "nationHasEmperorGeneral" to true,
        )
        cmd.env.gameStor["emperorGeneralId"] = 88L

        assertEquals(0, cmd.getPreReqTurn())
        assertEquals(24, cmd.getPostReqTurn())

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // Entity mutation: nation becomes emperor with legitimate type
        assertEquals(EmperorConstants.STATUS_EMPEROR, nation.meta[EmperorConstants.NATION_IMPERIAL_STATUS])
        assertEquals(EmperorConstants.TYPE_LEGITIMATE, nation.meta[EmperorConstants.NATION_EMPEROR_TYPE])
        // Emperor general: abdicated
        assertEquals(EmperorConstants.EMPEROR_ABDICATED, emperorGeneral.meta[EmperorConstants.GENERAL_EMPEROR_STATUS])
        assertEquals(0.toShort(), emperorGeneral.npcState)
        // Color-tagged log
        assertTrue(result.logs.any { it.contains("선양") })
    }

    @Test
    fun `신속 -- entity mutation and color-tagged log`() {
        val chief = createGeneral(officerLevel = 20)
        val nation = createNation(id = 1)
        nation.meta[EmperorConstants.NATION_IMPERIAL_STATUS] = EmperorConstants.STATUS_INDEPENDENT
        val destNation = createNation(id = 2)
        destNation.meta[EmperorConstants.NATION_IMPERIAL_STATUS] = EmperorConstants.STATUS_EMPEROR

        val mocks = createMockServicesBundle()
        val cmd = che_신속(chief, env())
        cmd.services = mocks.services
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.destNation = destNation
        cmd.constraintEnv = mapOf(
            "emperorSystem" to true,
        )

        assertEquals(0, cmd.getPreReqTurn())
        assertEquals(12, cmd.getPostReqTurn())

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // Entity mutation: nation becomes vassal with suzerain set
        assertEquals(EmperorConstants.STATUS_VASSAL, nation.meta[EmperorConstants.NATION_IMPERIAL_STATUS])
        assertEquals(2L, nation.meta[EmperorConstants.NATION_SUZERAIN_ID])
        // Color-tagged log
        assertTrue(result.logs.any { it.contains("신속") })
        assertTrue(result.logs.any { it.contains("<S>신속</>") })
    }

    @Test
    fun `독립선언 -- entity mutation and color-tagged log`() {
        val chief = createGeneral(officerLevel = 20)
        val nation = createNation(id = 1)
        nation.meta[EmperorConstants.NATION_IMPERIAL_STATUS] = EmperorConstants.STATUS_VASSAL
        nation.meta[EmperorConstants.NATION_SUZERAIN_ID] = 2L

        val suzerainNation = createNation(id = 2)
        val mocks = createMockServicesBundle()
        `when`(mocks.services.nationRepository.findById(2L)).thenReturn(java.util.Optional.of(suzerainNation))

        val cmd = che_독립선언(chief, env())
        cmd.services = mocks.services
        cmd.city = createCity(nationId = 1)
        cmd.nation = nation
        cmd.constraintEnv = mapOf(
            "emperorSystem" to true,
        )

        assertEquals(0, cmd.getPreReqTurn())
        assertEquals(12, cmd.getPostReqTurn())

        val result = runBlocking { cmd.run(fixedRng) }
        assertTrue(result.success)
        // Entity mutation: nation becomes independent, suzerain removed
        assertEquals(EmperorConstants.STATUS_INDEPENDENT, nation.meta[EmperorConstants.NATION_IMPERIAL_STATUS])
        assertTrue(nation.meta[EmperorConstants.NATION_SUZERAIN_ID] == null, "suzerain ID removed")
        // Color-tagged log
        assertTrue(result.logs.any { it.contains("독립") })
        assertTrue(result.logs.any { it.contains("<R><b>【독립】</b>") })
    }

    private fun createMockServicesBundle(): MockServicesBundle {
        val generalRepository = mock(com.opensam.repository.GeneralRepository::class.java)
        val cityRepository = mock(com.opensam.repository.CityRepository::class.java)
        val nationRepository = mock(com.opensam.repository.NationRepository::class.java)
        val diplomacyService = mock(com.opensam.engine.DiplomacyService::class.java)
        return MockServicesBundle(
            services = CommandServices(
                generalRepository = generalRepository,
                cityRepository = cityRepository,
                nationRepository = nationRepository,
                diplomacyService = diplomacyService,
            ),
            generalRepository = generalRepository,
            diplomacyService = diplomacyService,
        )
    }

    private data class MockServicesBundle(
        val services: CommandServices,
        val generalRepository: com.opensam.repository.GeneralRepository,
        val diplomacyService: com.opensam.engine.DiplomacyService,
    )
}
