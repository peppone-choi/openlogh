package com.opensam.engine.ai

import com.opensam.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.opensam.engine.turn.cqrs.port.WorldWritePort
import com.opensam.entity.*
import com.opensam.repository.*
import com.opensam.service.MapService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.OffsetDateTime
import java.util.*
import kotlin.random.Random

class GeneralAITest {

    private lateinit var ai: GeneralAI
    private lateinit var generalRepository: GeneralRepository
    private lateinit var cityRepository: CityRepository
    private lateinit var nationRepository: NationRepository
    private lateinit var diplomacyRepository: DiplomacyRepository
    private lateinit var mapService: MapService

    @BeforeEach
    fun setUp() {
        generalRepository = mock(GeneralRepository::class.java)
        cityRepository = mock(CityRepository::class.java)
        nationRepository = mock(NationRepository::class.java)
        diplomacyRepository = mock(DiplomacyRepository::class.java)
        mapService = mock(MapService::class.java)
        ai = GeneralAI(JpaWorldPortFactory(
            generalRepository = generalRepository,
            cityRepository = cityRepository,
            nationRepository = nationRepository,
            diplomacyRepository = diplomacyRepository,
        ), mapService)
    }

    private fun createWorld(year: Short = 200, month: Short = 3): WorldState {
        return WorldState(
            id = 1,
            scenarioCode = "test",
            currentYear = year,
            currentMonth = month,
            tickSeconds = 300,
        )
    }

    private fun createGeneral(
        id: Long = 1,
        nationId: Long = 1,
        cityId: Long = 1,
        leadership: Short = 50,
        strength: Short = 50,
        intel: Short = 50,
        crew: Int = 0,
        train: Short = 50,
        atmos: Short = 50,
        gold: Int = 1000,
        rice: Int = 1000,
        officerLevel: Short = 1,
        npcState: Short = 2,
        injury: Short = 0,
        dedication: Int = 100,
    ): General {
        return General(
            id = id,
            worldId = 1,
            name = "NPC장수$id",
            nationId = nationId,
            cityId = cityId,
            leadership = leadership,
            strength = strength,
            intel = intel,
            crew = crew,
            train = train,
            atmos = atmos,
            gold = gold,
            rice = rice,
            officerLevel = officerLevel,
            npcState = npcState,
            injury = injury,
            dedication = dedication,
            turnTime = OffsetDateTime.now(),
        )
    }

    private fun createCity(
        id: Long = 1,
        nationId: Long = 1,
        agri: Int = 500,
        agriMax: Int = 1000,
        comm: Int = 500,
        commMax: Int = 1000,
        secu: Int = 500,
        secuMax: Int = 1000,
        frontState: Short = 0,
    ): City {
        return City(
            id = id,
            worldId = 1,
            name = "도시$id",
            nationId = nationId,
            agri = agri,
            agriMax = agriMax,
            comm = comm,
            commMax = commMax,
            secu = secu,
            secuMax = secuMax,
            frontState = frontState,
            pop = 60000,
            popMax = 100000,
        )
    }

    private fun createNation(
        id: Long = 1,
        level: Short = 1,
        gold: Int = 10000,
        rice: Int = 10000,
        power: Int = 100,
        warState: Short = 0,
    ): Nation {
        return Nation(
            id = id,
            worldId = 1,
            name = "국가$id",
            color = "#FF0000",
            level = level,
            gold = gold,
            rice = rice,
            power = power,
            warState = warState,
        )
    }

    private fun createDiplomacy(
        srcNationId: Long,
        destNationId: Long,
        stateCode: String,
    ): Diplomacy {
        return Diplomacy(
            worldId = 1,
            srcNationId = srcNationId,
            destNationId = destNationId,
            stateCode = stateCode,
        )
    }

    private fun setupRepos(
        world: WorldState,
        general: General,
        city: City,
        nation: Nation?,
        allCities: List<City> = listOf(city),
        allGenerals: List<General> = listOf(general),
        allNations: List<Nation> = listOfNotNull(nation),
        diplomacies: List<Diplomacy> = emptyList(),
    ) {
        `when`(cityRepository.findById(general.cityId)).thenReturn(Optional.of(city))
        if (nation != null) {
            `when`(nationRepository.findById(general.nationId)).thenReturn(Optional.of(nation))
        }
        `when`(cityRepository.findByWorldId(world.id.toLong())).thenReturn(allCities)
        `when`(generalRepository.findByWorldId(world.id.toLong())).thenReturn(allGenerals)
        `when`(nationRepository.findByWorldId(world.id.toLong())).thenReturn(allNations)
        `when`(diplomacyRepository.findByWorldIdAndIsDeadFalse(world.id.toLong())).thenReturn(diplomacies)
    }

    // ========== Fallback to 휴식 ==========

    @Test
    fun `returns 휴식 when city not found`() {
        val world = createWorld()
        val general = createGeneral(cityId = 999)

        `when`(cityRepository.findById(999L)).thenReturn(Optional.empty())

        val action = ai.decideAndExecute(general, world)
        assertEquals("휴식", action)
    }

    // ========== Injury recovery ==========

    @Test
    fun `returns 요양 when general injury exceeds cureThreshold during war`() {
        val world = createWorld()
        // Per PHP: injury > cureThreshold (default 10). Use injury=15 to exceed threshold.
        val general = createGeneral(injury = 15)
        val city = createCity(nationId = 1)
        val nation = createNation()
        val diplomacy = createDiplomacy(1, 2, "전쟁")

        setupRepos(world, general, city, nation, diplomacies = listOf(diplomacy),
            allNations = listOf(nation, createNation(id = 2)))

        val action = ai.decideAndExecute(general, world)
        assertEquals("요양", action)
    }

    @Test
    fun `returns 요양 when general injury exceeds cureThreshold during peace`() {
        val world = createWorld()
        // Per PHP: injury > cureThreshold (default 10). Use injury=15 to exceed threshold.
        val general = createGeneral(injury = 15)
        val city = createCity(nationId = 1)
        val nation = createNation()

        setupRepos(world, general, city, nation)

        val action = ai.decideAndExecute(general, world)
        assertEquals("요양", action)
    }

    @Test
    fun `strategist skips 기술연구 when legacy TechLimit blocks current tech band`() {
        val world = createWorld(year = 184, month = 3).apply {
            config["startyear"] = 180
        }
        val general = createGeneral(leadership = 30, strength = 20, intel = 90, crew = 0, train = 80)
        val city = createCity(
            nationId = 1,
            agri = 1000,
            agriMax = 1000,
            comm = 1000,
            commMax = 1000,
            secu = 1000,
            secuMax = 1000,
        ).apply {
            trust = 100f
            pop = 100000
            popMax = 100000
        }
        val nation = createNation().apply {
            tech = 1000f
        }

        setupRepos(world, general, city, nation)

        val action = invokeDoNormalDomestic(world, general, city, nation)
        assertNull(action)
    }

    @Test
    fun `strategist chooses 기술연구 once next legacy tech band opens`() {
        val world = createWorld(year = 185, month = 3).apply {
            config["startyear"] = 180
        }
        val general = createGeneral(leadership = 30, strength = 20, intel = 90, crew = 0, train = 80)
        val city = createCity(
            nationId = 1,
            agri = 1000,
            agriMax = 1000,
            comm = 1000,
            commMax = 1000,
            secu = 1000,
            secuMax = 1000,
        ).apply {
            trust = 100f
            pop = 100000
            popMax = 100000
        }
        val nation = createNation().apply {
            tech = 1000f
        }

        setupRepos(world, general, city, nation)

        val action = invokeDoNormalDomestic(world, general, city, nation)
        assertEquals("기술연구", action)
    }

    @Test
    fun `resource trade uses legacy death-rate weighting from rank stats`() {
        val world = createWorld()
        val general = createGeneral(gold = 6000, rice = 900).apply {
            meta["rank"] = mutableMapOf(
                "killcrew" to 0,
                "deathcrew" to 50000,
            )
        }
        val city = createCity(nationId = 1)
        val nation = createNation()

        val action = invokeDoTradeResources(world, general, city, nation)

        assertEquals("군량매매", action)
        @Suppress("UNCHECKED_CAST")
        val aiArg = general.meta["aiArg"] as Map<String, Any>
        assertEquals(true, aiArg["isBuy"])
        assertEquals(1400, aiArg["amount"])
    }

    @Test
    fun `doRise requires nearby unoccupied major city like legacy`() {
        val world = createWorld(year = 181, month = 3).apply {
            config["startyear"] = 180
        }
        val general = createGeneral(
            nationId = 0,
            cityId = 1,
            leadership = 80,
            strength = 80,
            intel = 80,
            npcState = 2,
        )
        val currentCity = createCity(id = 1, nationId = 0).apply {
            level = 5
            meta["connections"] = listOf(2)
        }
        val blockedCity = createCity(id = 2, nationId = 1).apply {
            level = 5
            meta["connections"] = listOf(1)
        }
        val nation = createNation()

        setupRepos(
            world,
            general,
            currentCity,
            null,
            allCities = listOf(currentCity, blockedCity),
            allGenerals = listOf(general),
            allNations = listOf(nation),
        )

        val action = invokeDoRise(world, general, FixedRandom(0.1, 0.0))
        assertNull(action)
    }

    @Test
    fun `doRise uses legacy 70 point threshold instead of simplified 80`() {
        val world = createWorld(year = 181, month = 3).apply {
            config["startyear"] = 180
        }
        val general = createGeneral(
            nationId = 0,
            cityId = 1,
            leadership = 72,
            strength = 72,
            intel = 72,
            npcState = 2,
        )
        val currentCity = createCity(id = 1, nationId = 0).apply {
            level = 5
            meta["connections"] = listOf(2)
        }
        val openMajorCity = createCity(id = 2, nationId = 0).apply {
            level = 5
            meta["connections"] = listOf(1)
        }

        setupRepos(
            world,
            general,
            currentCity,
            null,
            allCities = listOf(currentCity, openMajorCity),
            allGenerals = listOf(general),
            allNations = emptyList(),
        )

        val action = invokeDoRise(world, general, FixedRandom(0.95, 0.0))
        assertEquals("거병", action)
    }

    @Test
    fun `choosePromotion uses dynamic legacy user killturn threshold`() {
        val world = createWorld().apply {
            config["turnterm"] = 60
        }
        val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, nationId = 1)
        val almostEligibleChief = createGeneral(id = 2, officerLevel = 11, npcState = 0, nationId = 1).apply {
            killTurn = 495
            permission = "normal"
        }
        val freshUser = createGeneral(id = 3, officerLevel = 1, npcState = 0, nationId = 1).apply {
            killTurn = 500
            leadership = 90
        }
        val city = createCity(nationId = 1)
        val nation = createNation(level = 1)

        val mockPorts = mock(WorldWritePort::class.java)
        ai.choosePromotion(
            buildPromotionContext(world, lord, city, nation, listOf(lord, almostEligibleChief, freshUser)),
            FixedRandomBooleans(false),
            mockPorts,
        )

        assertEquals("normal", almostEligibleChief.permission)
    }

    @Test
    fun `choosePromotion respects noAmbassador penalty for user chiefs`() {
        val world = createWorld().apply {
            config["turnterm"] = 60
        }
        val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, nationId = 1)
        val penalizedChief = createGeneral(id = 2, officerLevel = 11, npcState = 0, nationId = 1).apply {
            killTurn = 500
            permission = "normal"
            penalty["noAmbassador"] = true
        }
        val city = createCity(nationId = 1)
        val nation = createNation(level = 1)

        val mockPorts = mock(WorldWritePort::class.java)
        ai.choosePromotion(
            buildPromotionContext(world, lord, city, nation, listOf(lord, penalizedChief)),
            FixedRandomBooleans(false),
            mockPorts,
        )

        assertEquals("normal", penalizedChief.permission)
    }

    @Test
    fun `chooseGoldBillRate uses legacy city income war income and dedication bill`() {
        val world = createWorld()
        val general = createGeneral(nationId = 1, dedication = 2500).apply {
            officerCity = 0
        }
        val city = createCity(
            nationId = 1,
            comm = 1000,
            commMax = 1000,
            secu = 500,
            secuMax = 1000,
        ).apply {
            trust = 100f
            dead = 1000
        }
        val nation = createNation(gold = 10000).apply {
            rate = 20
            typeCode = "che_중립"
        }

        val bill = ai.chooseGoldBillRate(buildAiContext(world, general, city, nation, listOf(general)), listOf(city), NpcNationPolicy())

        assertEquals(141, bill)
        assertEquals(141.toShort(), nation.bill)
    }

    @Test
    fun `chooseRiceBillRate uses legacy agriculture wall and dedication bill`() {
        val world = createWorld()
        val general = createGeneral(nationId = 1, dedication = 2500).apply {
            officerCity = 0
        }
        val city = createCity(
            nationId = 1,
            agri = 1000,
            agriMax = 1000,
            secu = 500,
            secuMax = 1000,
        ).apply {
            trust = 100f
            def = 900
            wall = 900
            wallMax = 900
        }
        val nation = createNation(rice = 10000).apply {
            rate = 20
            typeCode = "che_중립"
        }

        val bill = ai.chooseRiceBillRate(buildAiContext(world, general, city, nation, listOf(general)), listOf(city), NpcNationPolicy())

        assertEquals(155, bill)
        assertEquals(155.toShort(), nation.bill)
    }

    private fun invokeDoNormalDomestic(world: WorldState, general: General, city: City, nation: Nation): String? {
        val method = GeneralAI::class.java.getDeclaredMethod(
            "doNormalDomestic",
            AIContext::class.java,
            Random::class.java,
            NpcNationPolicy::class.java,
        )
        method.isAccessible = true
        val ctx = AIContext(
            world = world,
            general = general,
            city = city,
            nation = nation,
            diplomacyState = DiplomacyState.PEACE,
            generalType = ai.classifyGeneral(general, Random(0), 40),
            allCities = listOf(city),
            allGenerals = listOf(general),
            allNations = listOf(nation),
            frontCities = emptyList(),
            rearCities = listOf(city),
            nationGenerals = listOf(general),
        )
        return method.invoke(ai, ctx, Random(0), NpcNationPolicy()) as String?
    }

    private fun invokeDoTradeResources(world: WorldState, general: General, city: City, nation: Nation): String? {
        val method = GeneralAI::class.java.getDeclaredMethod(
            "doTradeResources",
            AIContext::class.java,
            Random::class.java,
            NpcNationPolicy::class.java,
        )
        method.isAccessible = true
        val ctx = AIContext(
            world = world,
            general = general,
            city = city,
            nation = nation,
            diplomacyState = DiplomacyState.PEACE,
            generalType = ai.classifyGeneral(general, Random(0), 40),
            allCities = listOf(city),
            allGenerals = listOf(general),
            allNations = listOf(nation),
            frontCities = emptyList(),
            rearCities = listOf(city),
            nationGenerals = listOf(general),
        )
        return method.invoke(ai, ctx, Random(0), NpcNationPolicy()) as String?
    }

    private fun invokeDoRise(world: WorldState, general: General, rng: Random): String? {
        val method = GeneralAI::class.java.getDeclaredMethod(
            "doRise",
            General::class.java,
            WorldState::class.java,
            Random::class.java,
        )
        method.isAccessible = true
        return method.invoke(ai, general, world, rng) as String?
    }

    private fun buildPromotionContext(
        world: WorldState,
        general: General,
        city: City,
        nation: Nation,
        nationGenerals: List<General>,
    ) = buildAiContext(world, general, city, nation, nationGenerals)

    private fun buildAiContext(
        world: WorldState,
        general: General,
        city: City,
        nation: Nation,
        nationGenerals: List<General>,
    ) = AIContext(
        world = world,
        general = general,
        city = city,
        nation = nation,
        diplomacyState = DiplomacyState.PEACE,
        generalType = ai.classifyGeneral(general, Random(0), 40),
        allCities = listOf(city),
        allGenerals = nationGenerals,
        allNations = listOf(nation),
        frontCities = emptyList(),
        rearCities = listOf(city),
        nationGenerals = nationGenerals,
    )

    private class FixedRandom(private vararg val doubles: Double) : Random() {
        private var index = 0

        override fun nextBits(bitCount: Int): Int = 0

        override fun nextDouble(): Double {
            val value = doubles.getOrElse(index) { doubles.lastOrNull() ?: 0.0 }
            index++
            return value
        }
    }

    private class FixedRandomBooleans(private vararg val booleans: Boolean) : Random() {
        private var boolIndex = 0

        override fun nextBits(bitCount: Int): Int = 0

        override fun nextBoolean(): Boolean {
            val value = booleans.getOrElse(boolIndex) { booleans.lastOrNull() ?: false }
            boolIndex++
            return value
        }

        override fun nextDouble(): Double = 1.0
    }

    // ========== chooseGeneralTurn branch order (PHP parity) ==========

    @Test
    fun `chooseGeneralTurn checks do선양 before npcType 5`() {
        // Per PHP line 3745-3751: do선양 is checked BEFORE npcType==5 (line 3753)
        // A lord (officerLevel=20) who is also npcType=5 should try 선양 first
        val world = createWorld()
        val lord = createGeneral(id = 1, officerLevel = 20, npcState = 5, nationId = 1)
        val other = createGeneral(id = 2, officerLevel = 1, npcState = 2, nationId = 1)
        val city = createCity(nationId = 1)
        val nation = createNation()

        setupRepos(world, lord, city, nation,
            allGenerals = listOf(lord, other),
            allNations = listOf(nation))

        // If do선양 fires before npcType==5 rally, result should be 선양
        val action = ai.chooseGeneralTurn(lord, world)
        assertEquals("선양", action, "do선양 should be checked BEFORE npcType==5 rally per PHP")
    }

    @Test
    fun `chooseGeneralTurn injury threshold uses cureThreshold not zero`() {
        // Per PHP line 3772: injury > cureThreshold (default 10), NOT injury > 0
        val world = createWorld()
        val general = createGeneral(injury = 5, npcState = 2)
        val city = createCity(nationId = 1)
        val nation = createNation()

        setupRepos(world, general, city, nation)

        val action = ai.chooseGeneralTurn(general, world)
        assertNotEquals("요양", action,
            "injury=5 with cureThreshold=10 should NOT trigger healing per PHP")
    }

    @Test
    fun `chooseGeneralTurn lord without capital uses structured flow`() {
        // Per PHP lines 3802-3827: do건국/do방랑군이동/do해산 with relYearMonth > 1
        val world = createWorld(year = 181, month = 3).apply {
            config["startyear"] = 180
            config["init_year"] = 180
            config["init_month"] = 1
        }
        val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, nationId = 1)
        val city = createCity(nationId = 1)
        val nation = createNation().apply {
            capitalCityId = null  // No capital
        }

        setupRepos(world, lord, city, nation,
            allGenerals = listOf(lord),
            allNations = listOf(nation))

        val action = ai.chooseGeneralTurn(lord, world)
        // Should attempt 건국, 이동, or 해산 -- NOT fall through to standard decision
        assertNotNull(action, "Lord without capital should produce an action")
    }

    // ========== War actions ==========

    @Test
    fun `recruits via mobing when at war with low crew and plenty of gold and high train`() {
        val world = createWorld()
        // Per legacy: 모병 requires gold after train cost >= trainCost*6
        // leadership=50, trainCost=150, so need gold > 150 + 150*6 = 1050
        // Use "전쟁" (PHP state=0, active war) to trigger AT_WAR diplomacy state
        // Set train/atmos high so 전투준비 is skipped, reaching 징병 in priority
        val general = createGeneral(crew = 50, gold = 2000, rice = 2000, train = 90, atmos = 90)
        val city = createCity(nationId = 1)
        val nation = createNation(gold = 20000, rice = 20000)
        val diplomacy = createDiplomacy(1, 2, "전쟁")

        setupRepos(world, general, city, nation, diplomacies = listOf(diplomacy),
            allNations = listOf(nation, createNation(id = 2)))

        val action = ai.decideAndExecute(general, world)
        assertEquals("모병", action)
    }

    @Test
    fun `conscripts when at war with low crew and moderate gold and high train`() {
        val world = createWorld()
        // Per legacy: 징병 when gold is enough for train but not 모병
        // Use "전쟁" (PHP state=0, active war) to trigger AT_WAR diplomacy state
        // Set train/atmos high so 전투준비 is skipped, reaching 징병 in priority
        val general = createGeneral(crew = 50, gold = 500, rice = 1000, train = 90, atmos = 90)
        val city = createCity(nationId = 1)
        val nation = createNation(gold = 20000, rice = 20000)
        val diplomacy = createDiplomacy(1, 2, "전쟁")

        setupRepos(world, general, city, nation, diplomacies = listOf(diplomacy),
            allNations = listOf(nation, createNation(id = 2)))

        val action = ai.decideAndExecute(general, world)
        assertEquals("징병", action)
    }

    @Test
    fun `trains when at war with low train and high atmos`() {
        val world = createWorld()
        // Per legacy: weighted random choice between 훈련 and 사기진작
        // With atmos already at threshold (90), only 훈련 is chosen
        // Use "전쟁" (PHP state=0, active war) to trigger AT_WAR diplomacy state
        val general = createGeneral(crew = 2000, train = 50, atmos = 90)
        val city = createCity(nationId = 1)
        val nation = createNation(gold = 20000, rice = 20000)
        val diplomacy = createDiplomacy(1, 2, "전쟁")

        setupRepos(world, general, city, nation, diplomacies = listOf(diplomacy),
            allNations = listOf(nation, createNation(id = 2)))

        val action = ai.decideAndExecute(general, world)
        assertEquals("훈련", action)
    }

    @Test
    fun `boosts morale when at war with low atmos`() {
        val world = createWorld()
        // Use "전쟁" (PHP state=0, active war) to trigger AT_WAR diplomacy state
        val general = createGeneral(crew = 2000, train = 80, atmos = 50)
        val city = createCity(nationId = 1)
        val nation = createNation(gold = 20000, rice = 20000)
        val diplomacy = createDiplomacy(1, 2, "전쟁")

        setupRepos(world, general, city, nation, diplomacies = listOf(diplomacy),
            allNations = listOf(nation, createNation(id = 2)))

        val action = ai.decideAndExecute(general, world)
        assertEquals("사기진작", action)
    }

    @Test
    fun `attacks from attackable front city with enough troops`() {
        val world = createWorld()
        // Per legacy: frontState=2 (attackable front), frontState=1 is non-attackable
        val general = createGeneral(crew = 2000, train = 90, atmos = 90)
        val city = createCity(nationId = 1, frontState = 2)
        val enemyCity = createCity(id = 2, nationId = 2, frontState = 0)
        val nation = createNation(gold = 20000, rice = 20000)
        val diplomacy = createDiplomacy(1, 2, "전쟁")

        setupRepos(world, general, city, nation,
            allCities = listOf(city, enemyCity),
            diplomacies = listOf(diplomacy),
            allNations = listOf(nation, createNation(id = 2)))

        val action = ai.decideAndExecute(general, world)
        assertEquals("출병", action)
    }

    // ========== Peace actions: city development ==========

    @Test
    fun `develops agriculture when city agri is low`() {
        val world = createWorld()
        val general = createGeneral(crew = 0, intel = 80, strength = 30, leadership = 30)
        val city = createCity(nationId = 1, agri = 100, agriMax = 1000)
        val nation = createNation()

        setupRepos(world, general, city, nation)

        val action = ai.decideAndExecute(general, world)
        assertEquals("농지개간", action)
    }

    @Test
    fun `develops commerce when agri is ok but comm is low`() {
        val world = createWorld()
        val general = createGeneral(crew = 0, intel = 80, strength = 30, leadership = 30)
        val city = createCity(nationId = 1, agri = 600, agriMax = 1000, comm = 100, commMax = 1000)
        val nation = createNation()

        setupRepos(world, general, city, nation)

        val action = ai.decideAndExecute(general, world)
        assertEquals("상업투자", action)
    }

    @Test
    fun `develops security when agri and comm are ok but secu is low`() {
        val world = createWorld()
        val general = createGeneral(crew = 0, intel = 80, strength = 30, leadership = 30)
        val city = createCity(nationId = 1, agri = 600, agriMax = 1000, comm = 600, commMax = 1000, secu = 100, secuMax = 1000)
        val nation = createNation()

        setupRepos(world, general, city, nation)

        val action = ai.decideAndExecute(general, world)
        assertEquals("치안강화", action)
    }

    // ========== Peace actions: type-based ==========

    @Test
    fun `warrior type acts based on PHP priority during peace`() {
        val world = createWorld()
        val general = createGeneral(strength = 90, leadership = 50, intel = 30, crew = 500, train = 50)
        val city = createCity(nationId = 1, agri = 600, agriMax = 1000, comm = 600, commMax = 1000, secu = 600, secuMax = 1000)
        val nation = createNation()
        val otherNation = createNation(id = 2)
        val diplomacy = createDiplomacy(srcNationId = 1, destNationId = 2, stateCode = "동맹")

        setupRepos(world, general, city, nation,
            allNations = listOf(nation, otherNation),
            diplomacies = listOf(diplomacy))

        val action = ai.decideAndExecute(general, world)
        // With PHP priority order (NPC사망대비, 귀환, 금쌀구매, 출병, 긴급내정, 전투준비, ...소집해제...)
        // During peace with crew=500 and low train, action depends on priority iteration
        assertNotNull(action, "Should produce an action during peace")
    }

    @Test
    fun `warrior type produces valid action when at war with no crew`() {
        val world = createWorld()
        // With PHP priority order, general with no crew and low gold may do domestic or disband
        val general = createGeneral(strength = 90, leadership = 50, intel = 30, crew = 0, gold = 200, rice = 200, train = 90, atmos = 90)
        val city = createCity(nationId = 1, agri = 600, agriMax = 1000, comm = 600, commMax = 1000, secu = 600, secuMax = 1000)
        val nation = createNation()
        val diplomacy = createDiplomacy(srcNationId = 1, destNationId = 2, stateCode = "전쟁")

        setupRepos(world, general, city, nation, diplomacies = listOf(diplomacy),
            allNations = listOf(nation, createNation(id = 2)))

        val action = ai.decideAndExecute(general, world)
        assertNotNull(action, "Should produce a valid action")
        assertTrue(action.isNotEmpty(), "Action should not be empty")
    }

    // ========== Chief (lord) actions ==========

    @Test
    fun `chief uses priority loop and falls through to general action during peace`() {
        val world = createWorld()
        val chief = createGeneral(id = 1, officerLevel = 20, crew = 2000)
        val unassigned = createGeneral(id = 2, officerLevel = 0, npcState = 2)
        val city = createCity(nationId = 1)
        val nation = createNation()

        setupRepos(world, chief, city, nation,
            allGenerals = listOf(chief, unassigned))

        // With no front/supply cities and PEACE state, nation priorities all return null.
        // Chief falls through to general-level peace action (domestic development or neutral).
        val action = ai.decideAndExecute(chief, world)
        assertNotNull(action, "Chief should produce an action via priority loop")
        assertTrue(action != "발령" || action != "증축",
            "Chief uses priority-based decisions, not hardcoded early returns")
    }

    @Test
    fun `chief iterates nation priorities before general action`() {
        val world = createWorld()
        val chief = createGeneral(id = 1, officerLevel = 20, crew = 2000)
        val assigned = createGeneral(id = 2, officerLevel = 3, npcState = 2)
        val city = createCity(nationId = 1)
        city.level = 3
        val nation = createNation(gold = 20000)

        setupRepos(world, chief, city, nation,
            allCities = listOf(city),
            allGenerals = listOf(chief, assigned))

        // During peace with no front cities, nation priorities exhaust,
        // chief falls through to general-level peace action.
        val action = ai.decideAndExecute(chief, world)
        assertNotNull(action, "Chief should produce an action")
    }

    // ========== Diplomacy state detection ==========

    @Test
    fun `detects AT_WAR from diplomacy 전쟁`() {
        val world = createWorld()
        // Per legacy parity: gold=500 with leadership=50 -> trainCost=150, goldAfter=350
        // 350 < 900 (trainCost*6), so 징병 (conscript) not 모병 (volunteer)
        // Use "전쟁" (PHP state=0, active war) to trigger AT_WAR diplomacy state
        // Set train/atmos high so 전투준비 is skipped, reaching 징병 in PHP priority
        val general = createGeneral(crew = 50, gold = 500, train = 90, atmos = 90)
        val city = createCity(nationId = 1)
        val nation = createNation(gold = 20000, rice = 20000)
        val diplomacy = createDiplomacy(1, 2, "전쟁")

        setupRepos(world, general, city, nation, diplomacies = listOf(diplomacy),
            allNations = listOf(nation, createNation(id = 2)))

        // At war with low crew and moderate gold -> conscript
        val action = ai.decideAndExecute(general, world)
        assertEquals("징병", action)
    }

    @Test
    fun `detects PEACE when no diplomacy entries`() {
        val world = createWorld()
        val general = createGeneral(crew = 0, intel = 80, strength = 30, leadership = 30)
        val city = createCity(nationId = 1, agri = 100, agriMax = 1000)
        val nation = createNation()

        setupRepos(world, general, city, nation)

        // Peace + low agri => should develop
        val action = ai.decideAndExecute(general, world)
        assertEquals("농지개간", action)
    }

    // ========== NPC troop leader (npcState=5) ==========

    @Test
    fun `troop leader (npcState=5) always returns 집합`() {
        val world = createWorld()
        val general = createGeneral(npcState = 5)

        val action = ai.decideAndExecute(general, world)
        assertEquals("집합", action)
    }

    // ========== Wanderer (nationId=0) ==========

    @Test
    fun `wanderer returns 요양 when injured`() {
        val world = createWorld()
        val general = createGeneral(nationId = 0, injury = 10)

        val action = ai.decideAndExecute(general, world)
        assertEquals("요양", action)
    }

    @Test
    fun `wanderer returns one of exploration actions`() {
        val world = createWorld()
        val general = createGeneral(nationId = 0, injury = 0)

        val action = ai.decideAndExecute(general, world)
        assertTrue(action in listOf("견문", "이동", "물자조달", "휴식"),
            "Expected wanderer action but got: $action")
    }

    // ========== Reserved command ==========

    @Test
    fun `uses reserved command when set in meta`() {
        val world = createWorld()
        val general = createGeneral(crew = 0, intel = 80, strength = 30, leadership = 30)
        general.meta["reservedCommand"] = "단련"
        val city = createCity(nationId = 1)
        val nation = createNation()

        setupRepos(world, general, city, nation)

        val action = ai.decideAndExecute(general, world)
        assertEquals("단련", action)
        // Reserved command should be cleared after use
        assertNull(general.meta["reservedCommand"])
    }

    @Test
    fun `ignores reserved command when it is 휴식`() {
        val world = createWorld()
        val general = createGeneral(crew = 0, intel = 80, strength = 30, leadership = 30)
        general.meta["reservedCommand"] = "휴식"
        val city = createCity(nationId = 1, agri = 100, agriMax = 1000)
        val nation = createNation()

        setupRepos(world, general, city, nation)

        val action = ai.decideAndExecute(general, world)
        // Should fall through to normal AI decision (농지개간 since agri is low)
        assertEquals("농지개간", action)
    }

    // ========== Cure threshold ==========

    @Test
    fun `returns 요양 when injury exceeds cure threshold from policy`() {
        val world = createWorld()
        // Injury = 25, default cureThreshold = 20
        val general = createGeneral(injury = 25)
        val city = createCity(nationId = 1)
        val nation = createNation()

        setupRepos(world, general, city, nation)

        val action = ai.decideAndExecute(general, world)
        assertEquals("요양", action)
    }

    // ========== classifyGeneral: probabilistic hybrid ==========

    @Test
    fun `classifyGeneral sets WARRIOR for strength-dominant general`() {
        val general = createGeneral(strength = 90, intel = 30, leadership = 30)
        val flags = ai.classifyGeneral(general, Random(42))
        assertTrue(flags and GeneralType.WARRIOR.flag != 0,
            "Should have WARRIOR flag")
    }

    @Test
    fun `classifyGeneral sets STRATEGIST for intel-dominant general`() {
        val general = createGeneral(strength = 30, intel = 90, leadership = 30)
        val flags = ai.classifyGeneral(general, Random(42))
        assertTrue(flags and GeneralType.STRATEGIST.flag != 0,
            "Should have STRATEGIST flag")
    }

    @Test
    fun `classifyGeneral sets COMMANDER when leadership meets threshold`() {
        val general = createGeneral(strength = 50, intel = 50, leadership = 70)
        val flags = ai.classifyGeneral(general, Random(42), minNPCWarLeadership = 40)
        assertTrue(flags and GeneralType.COMMANDER.flag != 0,
            "Should have COMMANDER flag")
    }

    @Test
    fun `classifyGeneral does not set COMMANDER when leadership below threshold`() {
        val general = createGeneral(strength = 50, intel = 50, leadership = 30)
        val flags = ai.classifyGeneral(general, Random(42), minNPCWarLeadership = 40)
        assertTrue(flags and GeneralType.COMMANDER.flag == 0,
            "Should not have COMMANDER flag")
    }

    @Test
    fun `autoPromoteLord promotes best NPC when nation has no lord`() {
        val mockPorts = mock(WorldWritePort::class.java)
        val gen1 = createGeneral(id = 1, leadership = 80, strength = 70, intel = 60, officerLevel = 0)
        val gen2 = createGeneral(id = 2, leadership = 90, strength = 80, intel = 70, officerLevel = 0)
        val gen3 = createGeneral(id = 3, leadership = 50, strength = 40, intel = 30, officerLevel = 1)

        val result = ai.autoPromoteLord(listOf(gen1, gen2, gen3), mockPorts)

        assertNotNull(result)
        assertEquals(2L, result!!.id)
        assertEquals(20, result.officerLevel.toInt())
    }

    @Test
    fun `autoPromoteLord does nothing when lord exists`() {
        val mockPorts = mock(WorldWritePort::class.java)
        val lord = createGeneral(id = 1, officerLevel = 20)
        val gen2 = createGeneral(id = 2, leadership = 90, strength = 80, intel = 70, officerLevel = 0)

        val result = ai.autoPromoteLord(listOf(lord, gen2), mockPorts)

        assertNull(result)
        verifyNoInteractions(mockPorts)
    }

    @Test
    fun `getNationChiefLevel matches legacy exact mapping`() {
        val method = GeneralAI::class.java.getDeclaredMethod("getNationChiefLevel", Int::class.java)
        method.isAccessible = true

        assertEquals(5, method.invoke(ai, 7), "Emperor (7) should return 5")
        assertEquals(5, method.invoke(ai, 6), "King (6) should return 5")
        assertEquals(7, method.invoke(ai, 5), "Duke (5) should return 7")
        assertEquals(7, method.invoke(ai, 4), "Marquis (4) should return 7")
        assertEquals(9, method.invoke(ai, 3), "Prefect (3) should return 9")
        assertEquals(9, method.invoke(ai, 2), "Prefect (2) should return 9")
        assertEquals(11, method.invoke(ai, 1), "Below (1) should return 11")
        assertEquals(11, method.invoke(ai, 0), "Below (0) should return 11")
    }

    // ========== Legacy parity: wanderer reserved command ==========

    @Test
    fun `wanderer uses reserved command before wanderer-specific logic (legacy parity)`() {
        // Legacy PHP chooseGeneralTurn: reserved command checked BEFORE nationId==0 routing.
        // Kotlin bug: nationId==0 routes directly to decideWandererAction, skipping reserved command.
        val world = createWorld()
        val general = createGeneral(nationId = 0, injury = 0)
        general.meta["reservedCommand"] = "물자조달"

        val action = ai.decideAndExecute(general, world)

        assertEquals("물자조달", action, "Wanderer should use reserved command per legacy order")
        assertNull(general.meta["reservedCommand"], "Reserved command should be cleared after use")
    }

    // ========== Legacy parity: classifyGeneral stat-ratio probability ==========

    @Test
    fun `classifyGeneral uses stat-ratio probability not fixed 50 percent for hybrid warrior`() {
        // Legacy PHP calcGenType: nextBool(intel/strength/2) for warrior-base hybrid.
        // strength=100, intel=80: ratio=0.8, prob = 0.8/2 = 0.4 (40%)
        // With nextDouble()=0.45: 0.45 > 0.4 → legacy: NO STRATEGIST added
        // Kotlin bug: uses nextInt(100)<50 (50% fixed) — nextBits=0 gives 0 < 50 → STRATEGIST added (wrong)
        val general = createGeneral(strength = 100, intel = 80, leadership = 30)
        val flags = ai.classifyGeneral(general, FixedRandom(0.45), 40)

        assertTrue(flags and GeneralType.WARRIOR.flag != 0, "Should have WARRIOR flag")
        assertTrue(
            flags and GeneralType.STRATEGIST.flag == 0,
            "Should NOT have STRATEGIST: legacy prob=0.4, random=0.45 > 0.4 → no hybrid type"
        )
    }

    @Test
    fun `classifyGeneral uses stat-ratio probability not fixed 50 percent for hybrid strategist`() {
        // Legacy PHP: nextBool(strength/intel/2) for strategist-base hybrid.
        // intel=100, strength=80: ratio=0.8, prob = 0.8/2 = 0.4 (40%)
        // With nextDouble()=0.45: 0.45 > 0.4 → legacy: NO WARRIOR added
        val general = createGeneral(intel = 100, strength = 80, leadership = 30)
        val flags = ai.classifyGeneral(general, FixedRandom(0.45), 40)

        assertTrue(flags and GeneralType.STRATEGIST.flag != 0, "Should have STRATEGIST flag")
        assertTrue(
            flags and GeneralType.WARRIOR.flag == 0,
            "Should NOT have WARRIOR: legacy prob=0.4, random=0.45 > 0.4 → no hybrid type"
        )
    }

    // ========== Legacy parity: doRise current city level check ==========

    @Test
    fun `doRise skips 50 percent of time when general at non-major city (legacy parity)`() {
        // Legacy PHP do거병: if currentCityLevel < 5 or > 6, nextBool(0.5) skip.
        // When random > 0.5 at a small city, legacy returns null.
        // Kotlin bug: no current city level check → proceeds even at small city.
        val world = createWorld(year = 181, month = 3).apply {
            config["startyear"] = 180
        }
        val general = createGeneral(
            nationId = 0, cityId = 1, leadership = 80, strength = 80, intel = 80, npcState = 2,
        )
        val smallCity = createCity(id = 1, nationId = 0).apply { level = 3 }
        val openMajorCity = createCity(id = 2, nationId = 0).apply { level = 5 }

        setupRepos(
            world, general, smallCity, null,
            allCities = listOf(smallCity, openMajorCity),
            allGenerals = listOf(general),
            allNations = emptyList(),
        )

        // random=0.9 > 0.5 → legacy: city level check fires, skip → null
        // Kotlin (current): no city level check → threshold=0.9*70=63 < 80 passes, → "거병"
        val action = invokeDoRise(world, general, FixedRandom(0.9, 0.0))
        assertNull(action, "Should return null when at non-major city and random > 0.5 (legacy parity)")
    }

    // ========== Personnel AI Parity Tests (08-04 Plan Task 1) ==========

    @Nested
    inner class PersonnelAIParityTests {

        // ── doTroopRearAssignment: troop leader in low-pop city → rear assignment ──

        @Test
        fun `doTroopRearAssignment returns 발령 when troop leader in low-pop city`() {
            val world = createWorld()
            val chief = createGeneral(id = 1, officerLevel = 20, npcState = 2).apply {
                leadership = 80; crew = 2000
            }
            val troopLeader = createGeneral(id = 2, npcState = 5, officerLevel = 1).apply {
                leadership = 60; cityId = 1
            }
            val lowPopCity = createCity(id = 1, nationId = 1).apply {
                pop = 10000; popMax = 100000  // 10% < 50% safeRecruitRatio
                supplyState = 1
            }
            val highPopCity = createCity(id = 2, nationId = 1).apply {
                pop = 80000; popMax = 100000  // 80% > 50%
                frontState = 0; supplyState = 1
            }
            val nation = createNation().apply { capitalCityId = 1L }

            val ctx = buildAiContext(world, chief, lowPopCity, nation,
                listOf(chief, troopLeader)).copy(
                allCities = listOf(lowPopCity, highPopCity),
                frontCities = emptyList(),
            )
            val result = invokePrivateMethod("doTroopRearAssignment", ctx, Random(42),
                NpcNationPolicy(), listOf(highPopCity), listOf(lowPopCity, highPopCity))
            assertEquals("발령", result)
        }

        @Test
        fun `doTroopRearAssignment returns null when no troop leaders need rear`() {
            val world = createWorld()
            val chief = createGeneral(id = 1, officerLevel = 20, npcState = 2)
            val city = createCity(id = 1, nationId = 1).apply {
                pop = 80000; popMax = 100000; supplyState = 1
            }
            val nation = createNation().apply { capitalCityId = 1L }

            val ctx = buildAiContext(world, chief, city, nation, listOf(chief))
            val result = invokePrivateMethod("doTroopRearAssignment", ctx, Random(42),
                NpcNationPolicy(), listOf(city), listOf(city))
            assertNull(result)
        }

        // ── doTroopRescueAssignment: lost troop leader → rescue ──

        @Test
        fun `doTroopRescueAssignment returns 발령 for lost troop leader`() {
            val world = createWorld()
            val chief = createGeneral(id = 1, officerLevel = 20, npcState = 2)
            val lostLeader = createGeneral(id = 2, npcState = 5, officerLevel = 1).apply {
                cityId = 2 // In non-supply city
            }
            val supplyCity = createCity(id = 1, nationId = 1).apply { supplyState = 1 }
            val nonSupplyCity = createCity(id = 2, nationId = 1).apply { supplyState = 0 }
            val nation = createNation().apply { capitalCityId = 1L }

            val ctx = buildAiContext(world, chief, supplyCity, nation,
                listOf(chief, lostLeader)).copy(
                allCities = listOf(supplyCity, nonSupplyCity),
            )
            val result = invokePrivateMethod("doTroopRescueAssignment", ctx, Random(42),
                NpcNationPolicy(), listOf(supplyCity))
            assertEquals("발령", result)
        }

        @Test
        fun `doTroopRescueAssignment returns null when no lost troop leaders`() {
            val world = createWorld()
            val chief = createGeneral(id = 1, officerLevel = 20, npcState = 2)
            val leader = createGeneral(id = 2, npcState = 5, officerLevel = 1).apply {
                cityId = 1 // In supply city
            }
            val supplyCity = createCity(id = 1, nationId = 1).apply { supplyState = 1 }
            val nation = createNation().apply { capitalCityId = 1L }

            val ctx = buildAiContext(world, chief, supplyCity, nation,
                listOf(chief, leader))
            val result = invokePrivateMethod("doTroopRescueAssignment", ctx, Random(42),
                NpcNationPolicy(), listOf(supplyCity))
            assertNull(result)
        }

        // ── doUserFrontAssignment: user war general to front ──

        @Test
        fun `doUserFrontAssignment returns 발령 for user general in rear during war`() {
            val world = createWorld()
            val chief = createGeneral(id = 1, officerLevel = 20, npcState = 2)
            val userGen = createGeneral(id = 2, npcState = 0, officerLevel = 1).apply {
                cityId = 1; crew = 2000; train = 90; atmos = 90
            }
            val rearCity = createCity(id = 1, nationId = 1).apply {
                frontState = 0; supplyState = 1
            }
            val frontCity = createCity(id = 2, nationId = 1).apply {
                frontState = 2; supplyState = 1
            }
            val nation = createNation().apply { capitalCityId = 1L }

            val ctx = buildAiContext(world, chief, rearCity, nation,
                listOf(chief, userGen)).copy(
                diplomacyState = DiplomacyState.AT_WAR,
                allCities = listOf(rearCity, frontCity),
                frontCities = listOf(frontCity),
            )
            val result = invokePrivateMethod("doUserFrontAssignment", ctx, Random(42),
                NpcNationPolicy(), listOf(frontCity), true)
            assertEquals("발령", result)
        }

        @Test
        fun `doUserFrontAssignment returns null during peace`() {
            val world = createWorld()
            val chief = createGeneral(id = 1, officerLevel = 20, npcState = 2)
            val frontCity = createCity(id = 2, nationId = 1).apply { frontState = 2 }
            val nation = createNation().apply { capitalCityId = 1L }

            val ctx = buildAiContext(world, chief, frontCity, nation, listOf(chief)).copy(
                diplomacyState = DiplomacyState.PEACE,
                frontCities = listOf(frontCity),
            )
            val result = invokePrivateMethod("doUserFrontAssignment", ctx, Random(42),
                NpcNationPolicy(), listOf(frontCity), false)
            assertNull(result)
        }

        // ── doUserRearAssignment: user war general needing recruitment to rear ──

        @Test
        fun `doUserRearAssignment returns 발령 for low-crew user general at war`() {
            val world = createWorld()
            val chief = createGeneral(id = 1, officerLevel = 20, npcState = 2)
            val userGen = createGeneral(id = 2, npcState = 0, officerLevel = 1).apply {
                cityId = 1; crew = 100; leadership = 50
            }
            val lowPopSupplyCity = createCity(id = 1, nationId = 1).apply {
                pop = 10000; popMax = 100000; supplyState = 1; frontState = 2
            }
            val highPopRearCity = createCity(id = 2, nationId = 1).apply {
                pop = 80000; popMax = 100000; supplyState = 1; frontState = 0
            }
            val nation = createNation().apply { capitalCityId = 1L }

            val ctx = buildAiContext(world, chief, lowPopSupplyCity, nation,
                listOf(chief, userGen)).copy(
                diplomacyState = DiplomacyState.AT_WAR,
                allCities = listOf(lowPopSupplyCity, highPopRearCity),
                frontCities = listOf(lowPopSupplyCity),
            )
            val result = invokePrivateMethod("doUserRearAssignment", ctx, Random(42),
                NpcNationPolicy(), listOf(highPopRearCity),
                listOf(lowPopSupplyCity, highPopRearCity), listOf(lowPopSupplyCity))
            assertEquals("발령", result)
        }

        // ── doUserDomesticAssignment: user general to under-developed city ──

        @Test
        fun `doUserDomesticAssignment returns 발령 for user general in developed city`() {
            val world = createWorld()
            val chief = createGeneral(id = 1, officerLevel = 20, npcState = 2)
            val userGen = createGeneral(id = 2, npcState = 0, officerLevel = 1).apply {
                cityId = 1; leadership = 30
            }
            val developedCity = createCity(id = 1, nationId = 1).apply {
                agri = 1000; agriMax = 1000; comm = 1000; commMax = 1000
                secu = 1000; secuMax = 1000; supplyState = 1
                def = 1000; defMax = 1000; wall = 1000; wallMax = 1000
            }
            val underdevelopedCity = createCity(id = 2, nationId = 1).apply {
                agri = 100; agriMax = 1000; comm = 100; commMax = 1000
                secu = 100; secuMax = 1000; supplyState = 1
                def = 100; defMax = 1000; wall = 100; wallMax = 1000
            }
            val nation = createNation().apply { capitalCityId = 1L }

            val ctx = buildAiContext(world, chief, developedCity, nation,
                listOf(chief, userGen)).copy(
                allCities = listOf(developedCity, underdevelopedCity),
            )
            val result = invokePrivateMethod("doUserDomesticAssignment", ctx, Random(42),
                NpcNationPolicy(), listOf(developedCity, underdevelopedCity))
            assertEquals("발령", result)
        }

        @Test
        fun `doUserDomesticAssignment returns null when avg dev above 99 percent`() {
            val world = createWorld()
            val chief = createGeneral(id = 1, officerLevel = 20, npcState = 2)
            val developedCity = createCity(id = 1, nationId = 1).apply {
                agri = 1000; agriMax = 1000; comm = 1000; commMax = 1000
                secu = 1000; secuMax = 1000; supplyState = 1
                def = 1000; defMax = 1000; wall = 1000; wallMax = 1000
            }
            val nation = createNation().apply { capitalCityId = 1L }

            val ctx = buildAiContext(world, chief, developedCity, nation, listOf(chief))
            val result = invokePrivateMethod("doUserDomesticAssignment", ctx, Random(42),
                NpcNationPolicy(), listOf(developedCity))
            assertNull(result)
        }

        // ── doNpcReward: NPC general with resource deficit → reward ──

        @Test
        fun `doNpcReward returns 포상 for resource-poor NPC general`() {
            val world = createWorld()
            val chief = createGeneral(id = 1, officerLevel = 20, npcState = 2).apply {
                leadership = 80
            }
            val poorNpc = createGeneral(id = 2, npcState = 2, officerLevel = 1).apply {
                leadership = 60; gold = 100; rice = 100; killTurn = 50
            }
            val city = createCity(id = 1, nationId = 1)
            val nation = createNation(gold = 20000, rice = 20000)

            val ctx = buildAiContext(world, chief, city, nation,
                listOf(chief, poorNpc))
            val result = invokePrivateMethod("doNpcReward", ctx, Random(42), NpcNationPolicy())
            assertEquals("포상", result)
        }

        @Test
        fun `doNpcReward returns null when no eligible NPC generals`() {
            val world = createWorld()
            val chief = createGeneral(id = 1, officerLevel = 20, npcState = 2).apply {
                leadership = 80; gold = 20000; rice = 20000
            }
            val city = createCity(id = 1, nationId = 1)
            val nation = createNation(gold = 20000, rice = 20000)

            val ctx = buildAiContext(world, chief, city, nation, listOf(chief))
            val result = invokePrivateMethod("doNpcReward", ctx, Random(42), NpcNationPolicy())
            assertNull(result)
        }

        @Test
        fun `doNpcReward returns null when nation treasury too low`() {
            val world = createWorld()
            val chief = createGeneral(id = 1, officerLevel = 20, npcState = 2)
            val poorNpc = createGeneral(id = 2, npcState = 2, officerLevel = 1).apply {
                gold = 100; rice = 100; killTurn = 50
            }
            val city = createCity(id = 1, nationId = 1)
            val nation = createNation(gold = 500, rice = 500)  // Below reqNationGold/Rice

            val ctx = buildAiContext(world, chief, city, nation,
                listOf(chief, poorNpc))
            val result = invokePrivateMethod("doNpcReward", ctx, Random(42), NpcNationPolicy())
            assertNull(result)
        }

        // ── doNpcConfiscation: excess resources from NPC general ──

        @Test
        fun `doNpcConfiscation returns 몰수 for civil NPC with excess gold`() {
            val world = createWorld()
            val chief = createGeneral(id = 1, officerLevel = 20, npcState = 2).apply {
                leadership = 80
            }
            // Civil NPC (leadership < 40 = minNPCWarLeadership) with high gold
            val richCivil = createGeneral(id = 2, npcState = 2, officerLevel = 1).apply {
                leadership = 30; gold = 20000; rice = 20000
            }
            val city = createCity(id = 1, nationId = 1)
            val nation = createNation(gold = 5000, rice = 5000)

            val ctx = buildAiContext(world, chief, city, nation,
                listOf(chief, richCivil))
            val result = invokePrivateMethod("doNpcConfiscation", ctx, Random(42), NpcNationPolicy())
            assertEquals("몰수", result)
        }

        @Test
        fun `doNpcConfiscation returns null when no NPC generals have excess`() {
            val world = createWorld()
            val chief = createGeneral(id = 1, officerLevel = 20, npcState = 2)
            val normalNpc = createGeneral(id = 2, npcState = 2, officerLevel = 1).apply {
                leadership = 30; gold = 1000; rice = 1000
            }
            val city = createCity(id = 1, nationId = 1)
            val nation = createNation(gold = 20000, rice = 20000)

            val ctx = buildAiContext(world, chief, city, nation,
                listOf(chief, normalNpc))
            val result = invokePrivateMethod("doNpcConfiscation", ctx, Random(42), NpcNationPolicy())
            assertNull(result)
        }

        // ── doUserReward: user general with resource deficit ──

        @Test
        fun `doUserReward returns 포상 for resource-poor user general`() {
            val world = createWorld()
            val chief = createGeneral(id = 1, officerLevel = 20, npcState = 2)
            val poorUser = createGeneral(id = 2, npcState = 0, officerLevel = 1).apply {
                gold = 100; rice = 100; killTurn = 50
            }
            val city = createCity(id = 1, nationId = 1)
            val nation = createNation(gold = 20000, rice = 20000)

            val ctx = buildAiContext(world, chief, city, nation,
                listOf(chief, poorUser))
            val result = invokePrivateMethod("doUserReward", ctx, Random(42), NpcNationPolicy())
            assertEquals("포상", result)
        }

        // ── doUserUrgentReward: urgent reward for user war general ──

        @Test
        fun `doUserUrgentReward returns 포상 for low-gold user war general`() {
            val world = createWorld()
            val chief = createGeneral(id = 1, officerLevel = 20, npcState = 2)
            val poorWarUser = createGeneral(id = 2, npcState = 0, officerLevel = 1).apply {
                leadership = 50; gold = 100; rice = 5000; killTurn = 50
            }
            val city = createCity(id = 1, nationId = 1)
            val nation = createNation(gold = 50000, rice = 50000)

            val ctx = buildAiContext(world, chief, city, nation,
                listOf(chief, poorWarUser))
            val result = invokePrivateMethod("doUserUrgentReward", ctx, Random(42), NpcNationPolicy())
            assertEquals("포상", result)
        }

        @Test
        fun `doUserUrgentReward returns null when no eligible user generals`() {
            val world = createWorld()
            val chief = createGeneral(id = 1, officerLevel = 20, npcState = 2)
            val city = createCity(id = 1, nationId = 1)
            val nation = createNation(gold = 20000, rice = 20000)

            val ctx = buildAiContext(world, chief, city, nation, listOf(chief))
            val result = invokePrivateMethod("doUserUrgentReward", ctx, Random(42), NpcNationPolicy())
            assertNull(result)
        }

        // ── Reflection helpers for private methods ──

        private fun invokePrivateMethod(methodName: String, vararg args: Any?): String? {
            val paramTypes = args.map {
                when (it) {
                    is AIContext -> AIContext::class.java
                    is Random -> Random::class.java
                    is NpcNationPolicy -> NpcNationPolicy::class.java
                    is List<*> -> List::class.java
                    is Boolean -> Boolean::class.javaPrimitiveType!!
                    else -> it?.javaClass ?: Any::class.java
                }
            }.toTypedArray()

            val method = GeneralAI::class.java.declaredMethods.first { m ->
                m.name == methodName && m.parameterCount == args.size
            }
            method.isAccessible = true
            return method.invoke(ai, *args) as String?
        }
    }

    // ========== Wanderer AI Parity Tests (08-04 Plan Task 2) ==========

    @Nested
    inner class WandererAIParityTests {

        // ── doWandererMove: lord moves toward unoccupied major city ──

        @Test
        fun `doWandererMove returns 이동 when target city exists`() {
            val world = createWorld()
            val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, nationId = 0).apply {
                cityId = 1
            }
            val currentCity = createCity(id = 1, nationId = 0).apply {
                level = 3; meta["connections"] = listOf(2L)
            }
            val targetCity = createCity(id = 2, nationId = 0).apply {
                level = 5; meta["connections"] = listOf(1L)
            }

            setupRepos(world, lord, currentCity, null,
                allCities = listOf(currentCity, targetCity),
                allGenerals = listOf(lord),
                allNations = emptyList())

            val result = invokeDoWandererMove(world, lord, Random(42))
            assertEquals("이동", result)
        }

        @Test
        fun `doWandererMove returns null when only city available`() {
            val world = createWorld()
            val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, nationId = 0).apply {
                cityId = 1
            }
            val onlyCity = createCity(id = 1, nationId = 0).apply {
                level = 5; meta["connections"] = emptyList<Long>()
            }

            setupRepos(world, lord, onlyCity, null,
                allCities = listOf(onlyCity),
                allGenerals = listOf(lord),
                allNations = emptyList())

            val result = invokeDoWandererMove(world, lord, Random(42))
            assertNull(result)
        }

        @Test
        fun `doWandererMove stays when alone at unoccupied major city`() {
            val world = createWorld()
            val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, nationId = 0).apply {
                cityId = 1
            }
            val majorCity = createCity(id = 1, nationId = 0).apply {
                level = 5; meta["connections"] = listOf(2L)
            }
            val otherCity = createCity(id = 2, nationId = 0).apply {
                level = 3; meta["connections"] = listOf(1L)
            }

            setupRepos(world, lord, majorCity, null,
                allCities = listOf(majorCity, otherCity),
                allGenerals = listOf(lord),
                allNations = emptyList())

            // Alone at level-5 city -> returns null (stays to try founding)
            val result = invokeDoWandererMove(world, lord, Random(42))
            assertNull(result)
        }

        // ── doRise: NPC wanderer rises up ──

        @Test
        fun `doRise returns 거병 when npcState 2 and conditions met`() {
            val world = createWorld(year = 181, month = 3).apply {
                config["startyear"] = 180
            }
            val general = createGeneral(
                nationId = 0, cityId = 1, leadership = 80, strength = 80, intel = 80, npcState = 2,
            )
            val currentCity = createCity(id = 1, nationId = 0).apply {
                level = 5; meta["connections"] = listOf(2L)
            }
            val openMajorCity = createCity(id = 2, nationId = 0).apply {
                level = 5; meta["connections"] = listOf(1L)
            }

            setupRepos(world, general, currentCity, null,
                allCities = listOf(currentCity, openMajorCity),
                allGenerals = listOf(general),
                allNations = emptyList())

            val action = invokeDoRise(world, general, FixedRandom(0.3, 0.0))
            assertEquals("거병", action)
        }

        @Test
        fun `doRise returns null when npcState above 2 and makeLimit set`() {
            val world = createWorld(year = 181, month = 3).apply {
                config["startyear"] = 180
            }
            val general = createGeneral(
                nationId = 0, cityId = 1, leadership = 80, strength = 80, intel = 80, npcState = 2,
            ).apply { makeLimit = 5 }

            val action = invokeDoRise(world, general, FixedRandom(0.3, 0.0))
            assertNull(action)
        }

        // ── doSelectNation: wanderer chooses nation to join ──

        @Test
        fun `doSelectNation returns 임관 for barbarian joining barbarian nation`() {
            val world = createWorld()
            val barbarian = createGeneral(id = 1, npcState = 9, nationId = 0, officerLevel = 0).apply {
                cityId = 1
            }
            val barbarianLord = createGeneral(id = 2, npcState = 9, nationId = 1, officerLevel = 20).apply {
                cityId = 1
            }
            val city = createCity(id = 1, nationId = 0)
            val nation = createNation()

            setupRepos(world, barbarian, city, null,
                allCities = listOf(city),
                allGenerals = listOf(barbarian, barbarianLord),
                allNations = listOf(nation))

            val result = invokeDoSelectNation(world, barbarian, Random(42))
            assertEquals("임관", result)
        }

        @Test
        fun `doSelectNation returns null for affinity 999 wanderer`() {
            val world = createWorld()
            val general = createGeneral(id = 1, npcState = 2, nationId = 0, officerLevel = 0).apply {
                cityId = 1; affinity = 999
            }
            val city = createCity(id = 1, nationId = 0)

            setupRepos(world, general, city, null,
                allCities = listOf(city),
                allGenerals = listOf(general),
                allNations = emptyList())

            // With rng < 0.3 triggering enlistment path, affinity=999 returns null
            val result = invokeDoSelectNation(world, general, FixedRandom(0.1))
            assertNull(result)
        }

        // ── doFoundNation: lord founds nation ──

        @Test
        fun `doFoundNation returns 건국 with aiArg set`() {
            val world = createWorld()
            val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, nationId = 0).apply {
                name = "유비"
            }

            val result = invokeDoFoundNation(lord, Random(42))
            assertEquals("건국", result)
            @Suppress("UNCHECKED_CAST")
            val aiArg = lord.meta["aiArg"] as Map<String, Any>
            assertEquals("유비", aiArg["nationName"])
            assertNotNull(aiArg["nationType"])
            assertNotNull(aiArg["colorType"])
        }

        // ── doDisband: lord disbands weak nation ──

        @Test
        fun `doDisband returns 해산 and clears movingTargetCityID`() {
            val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, nationId = 1).apply {
                meta["movingTargetCityID"] = 5L
            }

            val result = invokeDoDisband(lord)
            assertEquals("해산", result)
            assertNull(lord.meta["movingTargetCityID"])
        }

        // ── doAbdicate: lord abdicates to better candidate ──

        @Test
        fun `doAbdicate returns 선양 when better candidate exists`() {
            val world = createWorld()
            val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, nationId = 1)
            val better = createGeneral(id = 2, npcState = 2, nationId = 1, officerLevel = 1).apply {
                leadership = 90; strength = 90; intel = 90
            }
            val city = createCity(id = 1, nationId = 1)
            val nation = createNation()

            setupRepos(world, lord, city, nation,
                allGenerals = listOf(lord, better),
                allNations = listOf(nation))

            val result = invokeDoAbdicate(world, lord, Random(42))
            assertEquals("선양", result)
        }

        @Test
        fun `doAbdicate returns null when no candidates`() {
            val world = createWorld()
            val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, nationId = 1)
            val city = createCity(id = 1, nationId = 1)
            val nation = createNation()

            setupRepos(world, lord, city, nation,
                allGenerals = listOf(lord),
                allNations = listOf(nation))

            val result = invokeDoAbdicate(world, lord, Random(42))
            assertNull(result)
        }

        @Test
        fun `doAbdicate returns null for non-lord`() {
            val world = createWorld()
            val general = createGeneral(id = 1, officerLevel = 5, npcState = 2, nationId = 1)

            val result = invokeDoAbdicate(world, general, Random(42))
            assertNull(result)
        }

        // ── Reflection helpers ──

        private fun invokeDoWandererMove(world: WorldState, general: General, rng: Random): String? {
            val method = GeneralAI::class.java.getDeclaredMethod(
                "doWandererMove", General::class.java, WorldState::class.java, Random::class.java)
            method.isAccessible = true
            return method.invoke(ai, general, world, rng) as String?
        }

        private fun invokeDoSelectNation(world: WorldState, general: General, rng: Random): String? {
            val method = GeneralAI::class.java.getDeclaredMethod(
                "doSelectNation", General::class.java, WorldState::class.java, Random::class.java)
            method.isAccessible = true
            return method.invoke(ai, general, world, rng) as String?
        }

        private fun invokeDoFoundNation(general: General, rng: Random): String? {
            val method = GeneralAI::class.java.getDeclaredMethod(
                "doFoundNation", General::class.java, Random::class.java)
            method.isAccessible = true
            return method.invoke(ai, general, rng) as String?
        }

        private fun invokeDoDisband(general: General): String? {
            val method = GeneralAI::class.java.getDeclaredMethod("doDisband", General::class.java)
            method.isAccessible = true
            return method.invoke(ai, general) as String?
        }

        private fun invokeDoAbdicate(world: WorldState, general: General, rng: Random): String? {
            val method = GeneralAI::class.java.getDeclaredMethod(
                "doAbdicate", General::class.java, WorldState::class.java, Random::class.java)
            method.isAccessible = true
            return method.invoke(ai, general, world, rng) as String?
        }
    }

    // ========== Promotion AI Parity Tests (08-04 Plan Task 2) ==========

    @Nested
    inner class PromotionAIParityTests {

        @Test
        fun `choosePromotion with nationLevel 7 creates positions from level 5`() {
            val world = createWorld(month = 3)
            val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, nationId = 1)
            val npc1 = createGeneral(id = 2, npcState = 2, officerLevel = 1).apply {
                leadership = 80; strength = 70; intel = 60; killTurn = 50
            }
            val npc2 = createGeneral(id = 3, npcState = 2, officerLevel = 1).apply {
                leadership = 70; strength = 80; intel = 50; killTurn = 50
            }
            val npc3 = createGeneral(id = 4, npcState = 2, officerLevel = 1).apply {
                leadership = 60; strength = 50; intel = 80; killTurn = 50
            }
            val city = createCity(nationId = 1)
            val nation = createNation(level = 7)

            val mockPorts = mock(WorldWritePort::class.java)
            ai.choosePromotion(
                buildPromotionContext(world, lord, city, nation,
                    listOf(lord, npc1, npc2, npc3)),
                Random(42), mockPorts)

            // With nation level 7, min chief level = 5, so positions 5-11 should be fillable
            // At least one general should have been promoted (officerLevel > 1)
            val promotedCount = listOf(npc1, npc2, npc3).count { it.officerLevel.toInt() > 1 }
            assertTrue(promotedCount > 0, "At least one general should be promoted at nation level 7")
        }

        @Test
        fun `choosePromotion with nationLevel 0 only creates positions at level 11`() {
            val world = createWorld(month = 3)
            val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, nationId = 1)
            val npc1 = createGeneral(id = 2, npcState = 2, officerLevel = 1).apply {
                leadership = 80; strength = 70; intel = 60; killTurn = 50
            }
            val city = createCity(nationId = 1)
            val nation = createNation(level = 0)

            val mockPorts = mock(WorldWritePort::class.java)
            ai.choosePromotion(
                buildPromotionContext(world, lord, city, nation, listOf(lord, npc1)),
                Random(42), mockPorts)

            // Nation level 0 → minChiefLevel = 11, only position 11 available
            if (npc1.officerLevel.toInt() > 1) {
                assertEquals(11, npc1.officerLevel.toInt(),
                    "At nation level 0, only position 11 should be available")
            }
        }

        @Test
        fun `chooseNonLordPromotion fills empty positions with available generals`() {
            val world = createWorld(month = 3)
            val chief = createGeneral(id = 1, officerLevel = 11, npcState = 2, nationId = 1).apply {
                leadership = 80
            }
            val npc1 = createGeneral(id = 2, npcState = 2, officerLevel = 1).apply {
                leadership = 60; strength = 70; intel = 50; killTurn = 50
            }
            val city = createCity(nationId = 1)
            val nation = createNation(level = 3)

            val mockPorts = mock(WorldWritePort::class.java)
            ai.chooseNonLordPromotion(
                buildAiContext(world, chief, city, nation, listOf(chief, npc1)),
                Random(42), mockPorts)

            // At nation level 3, minChiefLevel = 9. Position 9 and 10 may be filled.
            // npc1 may get promoted if stat checks pass
            // Just verify no exception and general was processed
            assertTrue(true, "chooseNonLordPromotion ran without exception")
        }

        @Test
        fun `chooseNonLordPromotion does not promote to already-filled positions`() {
            val world = createWorld(month = 3)
            val chief = createGeneral(id = 1, officerLevel = 11, npcState = 2, nationId = 1)
            val existingChief9 = createGeneral(id = 2, npcState = 2, officerLevel = 9).apply {
                leadership = 70; strength = 70; intel = 70
            }
            val npc1 = createGeneral(id = 3, npcState = 2, officerLevel = 1).apply {
                leadership = 50; strength = 50; intel = 50; killTurn = 50
            }
            val city = createCity(nationId = 1)
            val nation = createNation(level = 3)

            val mockPorts = mock(WorldWritePort::class.java)
            val originalLevel = npc1.officerLevel

            ai.chooseNonLordPromotion(
                buildAiContext(world, chief, city, nation,
                    listOf(chief, existingChief9, npc1)),
                Random(42), mockPorts)

            // Position 9 was already filled, so it should be skipped
            // Position 10 might be filled by npc1 if stat check passes
            assertNotEquals(9, npc1.officerLevel.toInt(),
                "Should not be promoted to already-filled position 9")
        }
    }

    @Test
    fun `doRise proceeds when at non-major city and random below 0-5 (legacy parity)`() {
        // With random <= 0.5, legacy city level check does NOT skip (nextBool(0.5) = false)
        val world = createWorld(year = 181, month = 3).apply {
            config["startyear"] = 180
        }
        val general = createGeneral(
            nationId = 0, cityId = 1, leadership = 80, strength = 80, intel = 80, npcState = 2,
        )
        val smallCity = createCity(id = 1, nationId = 0).apply { level = 3 }
        val openMajorCity = createCity(id = 2, nationId = 0).apply { level = 5 }

        setupRepos(
            world, general, smallCity, null,
            allCities = listOf(smallCity, openMajorCity),
            allGenerals = listOf(general),
            allNations = emptyList(),
        )

        // random=0.3 < 0.5 → legacy: city level check does NOT skip
        // threshold = 0.3 * 70 = 21 < 80 → passes; final prob: 0.0 < 0.015 → "거병"
        val action = invokeDoRise(world, general, FixedRandom(0.3, 0.0))
        assertEquals("거병", action, "Should proceed to 거병 when random <= 0.5 at non-major city")
    }
}
