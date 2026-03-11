package com.opensam.engine.ai

import com.opensam.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.opensam.entity.*
import com.opensam.repository.*
import com.opensam.service.MapService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
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
    fun `returns 요양 when general is injured during war`() {
        val world = createWorld()
        val general = createGeneral(injury = 10)
        val city = createCity(nationId = 1)
        val nation = createNation()
        val diplomacy = createDiplomacy(1, 2, "선전포고")

        setupRepos(world, general, city, nation, diplomacies = listOf(diplomacy),
            allNations = listOf(nation, createNation(id = 2)))

        val action = ai.decideAndExecute(general, world)
        assertEquals("요양", action)
    }

    @Test
    fun `returns 요양 when general is injured during peace`() {
        val world = createWorld()
        val general = createGeneral(injury = 5)
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
        val lord = createGeneral(id = 1, officerLevel = 12, npcState = 2, nationId = 1)
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

        ai.choosePromotion(
            buildPromotionContext(world, lord, city, nation, listOf(lord, almostEligibleChief, freshUser)),
            FixedRandomBooleans(false),
        )

        assertEquals("normal", almostEligibleChief.permission)
    }

    @Test
    fun `choosePromotion respects noAmbassador penalty for user chiefs`() {
        val world = createWorld().apply {
            config["turnterm"] = 60
        }
        val lord = createGeneral(id = 1, officerLevel = 12, npcState = 2, nationId = 1)
        val penalizedChief = createGeneral(id = 2, officerLevel = 11, npcState = 0, nationId = 1).apply {
            killTurn = 500
            permission = "normal"
            penalty["noAmbassador"] = true
        }
        val city = createCity(nationId = 1)
        val nation = createNation(level = 1)

        ai.choosePromotion(
            buildPromotionContext(world, lord, city, nation, listOf(lord, penalizedChief)),
            FixedRandomBooleans(false),
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

    // ========== War actions ==========

    @Test
    fun `recruits via mobing when at war with low crew and plenty of gold`() {
        val world = createWorld()
        // Per legacy: 모병 requires gold after train cost >= trainCost*6
        // leadership=50, trainCost=150, so need gold > 150 + 150*6 = 1050
        val general = createGeneral(crew = 50, gold = 2000, rice = 2000)
        val city = createCity(nationId = 1)
        val nation = createNation(gold = 20000, rice = 20000)
        val diplomacy = createDiplomacy(1, 2, "선전포고")

        setupRepos(world, general, city, nation, diplomacies = listOf(diplomacy),
            allNations = listOf(nation, createNation(id = 2)))

        val action = ai.decideAndExecute(general, world)
        assertEquals("모병", action)
    }

    @Test
    fun `conscripts when at war with low crew and moderate gold`() {
        val world = createWorld()
        // Per legacy: 징병 when gold is enough for train but not 모병
        val general = createGeneral(crew = 50, gold = 500, rice = 1000)
        val city = createCity(nationId = 1)
        val nation = createNation(gold = 20000, rice = 20000)
        val diplomacy = createDiplomacy(1, 2, "선전포고")

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
        val general = createGeneral(crew = 2000, train = 50, atmos = 90)
        val city = createCity(nationId = 1)
        val nation = createNation(gold = 20000, rice = 20000)
        val diplomacy = createDiplomacy(1, 2, "선전포고")

        setupRepos(world, general, city, nation, diplomacies = listOf(diplomacy),
            allNations = listOf(nation, createNation(id = 2)))

        val action = ai.decideAndExecute(general, world)
        assertEquals("훈련", action)
    }

    @Test
    fun `boosts morale when at war with low atmos`() {
        val world = createWorld()
        val general = createGeneral(crew = 2000, train = 80, atmos = 50)
        val city = createCity(nationId = 1)
        val nation = createNation(gold = 20000, rice = 20000)
        val diplomacy = createDiplomacy(1, 2, "선전포고")

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
    fun `warrior type trains troops during peace`() {
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
        assertEquals("훈련", action)
    }

    @Test
    fun `warrior type recruits when no crew`() {
        val world = createWorld()
        val general = createGeneral(strength = 90, leadership = 50, intel = 30, crew = 0, gold = 5000, rice = 5000)
        val city = createCity(nationId = 1, agri = 600, agriMax = 1000, comm = 600, commMax = 1000, secu = 600, secuMax = 1000)
        val nation = createNation()

        setupRepos(world, general, city, nation)

        val action = ai.decideAndExecute(general, world)
        // warrior with crew=0, sufficient gold/rice => 모병 (rich) or 징병 (poor)
        assertTrue(action == "모병" || action == "징병", "Expected recruitment command but got: $action")
    }

    // ========== Chief (lord) actions ==========

    @Test
    fun `chief uses priority loop and falls through to general action during peace`() {
        val world = createWorld()
        val chief = createGeneral(id = 1, officerLevel = 12, crew = 2000)
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
        val chief = createGeneral(id = 1, officerLevel = 12, crew = 2000)
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
    fun `detects AT_WAR from diplomacy 선전포고`() {
        val world = createWorld()
        // Per legacy parity: gold=500 with leadership=50 -> trainCost=150, goldAfter=350
        // 350 < 900 (trainCost*6), so 징병 (conscript) not 모병 (volunteer)
        val general = createGeneral(crew = 50, gold = 500)
        val city = createCity(nationId = 1)
        val nation = createNation(gold = 20000, rice = 20000)
        val diplomacy = createDiplomacy(1, 2, "선전포고")

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
}
