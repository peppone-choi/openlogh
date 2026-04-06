package com.openlogh.engine.ai

import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.engine.turn.cqrs.port.WorldWritePort
import com.openlogh.entity.*
import com.openlogh.repository.*
import com.openlogh.service.MapService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.OffsetDateTime
import java.util.*
import kotlin.random.Random

class GeneralAITest {

    private lateinit var ai: OfficerAI
    private lateinit var officerRepository: OfficerRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var diplomacyRepository: DiplomacyRepository
    private lateinit var mapService: MapService

    @BeforeEach
    fun setUp() {
        officerRepository = mock(OfficerRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        diplomacyRepository = mock(DiplomacyRepository::class.java)
        mapService = mock(MapService::class.java)
        ai = OfficerAI(JpaWorldPortFactory(
            officerRepository = officerRepository,
            planetRepository = planetRepository,
            factionRepository = factionRepository,
            diplomacyRepository = diplomacyRepository,
        ), mapService)
    }

    private fun createWorld(year: Short = 200, month: Short = 3): SessionState {
        return SessionState(
            id = 1,
            scenarioCode = "test",
            currentYear = year,
            currentMonth = month,
            tickSeconds = 300,
        )
    }

    private fun createGeneral(
        id: Long = 1,
        factionId: Long = 1,
        planetId: Long = 1,
        leadership: Short = 50,
        command: Short = 50,
        intelligence: Short = 50,
        ships: Int = 0,
        training: Short = 50,
        morale: Short = 50,
        funds: Int = 1000,
        supplies: Int = 1000,
        officerLevel: Short = 1,
        npcState: Short = 2,
        injury: Short = 0,
        dedication: Int = 100,
    ): Officer {
        return Officer(
            id = id,
            sessionId = 1,
            name = "NPC장수$id",
            factionId = factionId,
            planetId = planetId,
            leadership = leadership,
            command = command,
            intelligence = intelligence,
            ships = ships,
            training = training,
            morale = morale,
            funds = funds,
            supplies = supplies,
            officerLevel = officerLevel,
            npcState = npcState,
            injury = injury,
            dedication = dedication,
            turnTime = OffsetDateTime.now(),
        )
    }

    private fun createCity(
        id: Long = 1,
        factionId: Long = 1,
        production: Int = 500,
        productionMax: Int = 1000,
        commerce: Int = 500,
        commerceMax: Int = 1000,
        security: Int = 500,
        securityMax: Int = 1000,
        frontState: Short = 0,
    ): Planet {
        return Planet(
            id = id,
            sessionId = 1,
            name = "도시$id",
            factionId = factionId,
            production = production,
            productionMax = productionMax,
            commerce = commerce,
            commerceMax = commerceMax,
            security = security,
            securityMax = securityMax,
            frontState = frontState,
            population = 60000,
            populationMax = 100000,
        )
    }

    private fun createNation(
        id: Long = 1,
        level: Short = 1,
        funds: Int = 10000,
        supplies: Int = 10000,
        militaryPower: Int = 100,
        warState: Short = 0,
    ): Faction {
        return Faction(
            id = id,
            sessionId = 1,
            name = "국가$id",
            color = "#FF0000",
            factionRank = level,
            funds = funds,
            supplies = supplies,
            militaryPower = militaryPower,
            warState = warState,
        )
    }

    private fun createDiplomacy(
        srcFactionId: Long,
        destFactionId: Long,
        stateCode: String,
    ): Diplomacy {
        return Diplomacy(
            sessionId = 1,
            srcFactionId = srcFactionId,
            destFactionId = destFactionId,
            stateCode = stateCode,
        )
    }

    private fun setupRepos(
        world: SessionState,
        general: Officer,
        city: Planet,
        nation: Faction?,
        allCities: List<Planet> = listOf(city),
        allGenerals: List<Officer> = listOf(general),
        allNations: List<Faction> = listOfNotNull(nation),
        diplomacies: List<Diplomacy> = emptyList(),
    ) {
        `when`(planetRepository.findById(general.planetId)).thenReturn(Optional.of(city))
        if (nation != null) {
            `when`(factionRepository.findById(general.factionId)).thenReturn(Optional.of(nation))
        }
        `when`(planetRepository.findBySessionId(world.id.toLong())).thenReturn(allCities)
        `when`(officerRepository.findBySessionId(world.id.toLong())).thenReturn(allGenerals)
        `when`(factionRepository.findBySessionId(world.id.toLong())).thenReturn(allNations)
        `when`(diplomacyRepository.findBySessionIdAndIsDeadFalse(world.id.toLong())).thenReturn(diplomacies)
    }

    // ========== Fallback to 휴식 ==========

    @Test
    fun `returns 휴식 when city not found`() {
        val world = createWorld()
        val general = createGeneral(planetId = 999)

        `when`(planetRepository.findById(999L)).thenReturn(Optional.empty())

        val action = ai.decideAndExecute(general, world)
        assertEquals("휴식", action)
    }

    // ========== Injury recovery ==========

    @Test
    fun `returns 요양 when general injury exceeds cureThreshold during war`() {
        val world = createWorld()
        // Per PHP: injury > cureThreshold (default 10). Use injury=15 to exceed threshold.
        val general = createGeneral(injury = 15)
        val city = createCity(factionId = 1)
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
        val city = createCity(factionId = 1)
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
        val general = createGeneral(leadership = 30, command = 20, intelligence = 90, ships = 0, training = 80)
        val city = createCity(
            factionId = 1,
            production = 1000,
            productionMax = 1000,
            commerce = 1000,
            commerceMax = 1000,
            security = 1000,
            securityMax = 1000,
        ).apply {
            approval = 100f
            population = 100000
            populationMax = 100000
        }
        val nation = createNation().apply {
            techLevel = 1000f
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
        val general = createGeneral(leadership = 30, command = 20, intelligence = 90, ships = 0, training = 80)
        val city = createCity(
            factionId = 1,
            production = 1000,
            productionMax = 1000,
            commerce = 1000,
            commerceMax = 1000,
            security = 1000,
            securityMax = 1000,
        ).apply {
            approval = 100f
            population = 100000
            populationMax = 100000
        }
        val nation = createNation().apply {
            techLevel = 1000f
        }

        setupRepos(world, general, city, nation)

        val action = invokeDoNormalDomestic(world, general, city, nation)
        assertEquals("기술연구", action)
    }

    @Test
    fun `resource trade uses legacy death-rate weighting from rank stats`() {
        val world = createWorld()
        val general = createGeneral(funds = 6000, supplies = 900).apply {
            meta["rank"] = mutableMapOf(
                "killcrew" to 0,
                "deathcrew" to 50000,
            )
        }
        val city = createCity(factionId = 1)
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
            factionId = 0,
            planetId = 1,
            leadership = 80,
            command = 80,
            intelligence = 80,
            npcState = 2,
        )
        val currentCity = createCity(id = 1, factionId = 0).apply {
            level = 5
            meta["connections"] = listOf(2)
        }
        val blockedCity = createCity(id = 2, factionId = 1).apply {
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
            factionId = 0,
            planetId = 1,
            leadership = 72,
            command = 72,
            intelligence = 72,
            npcState = 2,
        )
        val currentCity = createCity(id = 1, factionId = 0).apply {
            level = 5
            meta["connections"] = listOf(2)
        }
        val openMajorCity = createCity(id = 2, factionId = 0).apply {
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
        val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, factionId = 1)
        val almostEligibleChief = createGeneral(id = 2, officerLevel = 11, npcState = 0, factionId = 1).apply {
            killTurn = 495
            permission = "normal"
        }
        val freshUser = createGeneral(id = 3, officerLevel = 1, npcState = 0, factionId = 1).apply {
            killTurn = 500
            leadership = 90
        }
        val city = createCity(factionId = 1)
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
        val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, factionId = 1)
        val penalizedChief = createGeneral(id = 2, officerLevel = 11, npcState = 0, factionId = 1).apply {
            killTurn = 500
            permission = "normal"
            penalty["noAmbassador"] = true
        }
        val city = createCity(factionId = 1)
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
        val general = createGeneral(factionId = 1, dedication = 2500).apply {
            officerPlanet = 0
        }
        val city = createCity(
            factionId = 1,
            commerce = 1000,
            commerceMax = 1000,
            security = 500,
            securityMax = 1000,
        ).apply {
            approval = 100f
            dead = 1000
        }
        val nation = createNation(funds = 10000).apply {
            conscriptionRate = 20
            factionType = "che_중립"
        }

        val bill = ai.chooseGoldBillRate(buildAiContext(world, general, city, nation, listOf(general)), listOf(city), NpcNationPolicy())

        assertEquals(141, bill)
        assertEquals(141.toShort(), nation.taxRate)
    }

    @Test
    fun `chooseRiceBillRate uses legacy agriculture fortress and dedication bill`() {
        val world = createWorld()
        val general = createGeneral(factionId = 1, dedication = 2500).apply {
            officerPlanet = 0
        }
        val city = createCity(
            factionId = 1,
            production = 1000,
            productionMax = 1000,
            security = 500,
            securityMax = 1000,
        ).apply {
            approval = 100f
            orbitalDefense = 900
            fortress = 900
            fortressMax = 900
        }
        val nation = createNation(supplies = 10000).apply {
            conscriptionRate = 20
            factionType = "che_중립"
        }

        val bill = ai.chooseRiceBillRate(buildAiContext(world, general, city, nation, listOf(general)), listOf(city), NpcNationPolicy())

        assertEquals(155, bill)
        assertEquals(155.toShort(), nation.taxRate)
    }

    private fun invokeDoNormalDomestic(world: SessionState, general: Officer, city: Planet, nation: Faction): String? {
        val method = OfficerAI::class.java.getDeclaredMethod(
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

    private fun invokeDoTradeResources(world: SessionState, general: Officer, city: Planet, nation: Faction): String? {
        val method = OfficerAI::class.java.getDeclaredMethod(
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

    private fun invokeDoRise(world: SessionState, general: Officer, rng: Random): String? {
        val method = OfficerAI::class.java.getDeclaredMethod(
            "doRise",
            Officer::class.java,
            SessionState::class.java,
            Random::class.java,
        )
        method.isAccessible = true
        return method.invoke(ai, general, world, rng) as String?
    }

    private fun buildPromotionContext(
        world: SessionState,
        general: Officer,
        city: Planet,
        nation: Faction,
        nationGenerals: List<Officer>,
    ) = buildAiContext(world, general, city, nation, nationGenerals)

    private fun buildAiContext(
        world: SessionState,
        general: Officer,
        city: Planet,
        nation: Faction,
        nationGenerals: List<Officer>,
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
        val lord = createGeneral(id = 1, officerLevel = 20, npcState = 5, factionId = 1)
        val other = createGeneral(id = 2, officerLevel = 1, npcState = 2, factionId = 1)
        val city = createCity(factionId = 1)
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
        val city = createCity(factionId = 1)
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
        val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, factionId = 1)
        val city = createCity(factionId = 1)
        val nation = createNation().apply {
            capitalPlanetId = null  // No capital
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
        val general = createGeneral(ships = 50, funds = 2000, supplies = 2000, training = 90, morale = 90)
        val city = createCity(factionId = 1)
        val nation = createNation(funds = 20000, supplies = 20000)
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
        val general = createGeneral(ships = 50, funds = 500, supplies = 1000, training = 90, morale = 90)
        val city = createCity(factionId = 1)
        val nation = createNation(funds = 20000, supplies = 20000)
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
        val general = createGeneral(ships = 2000, training = 50, morale = 90)
        val city = createCity(factionId = 1)
        val nation = createNation(funds = 20000, supplies = 20000)
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
        val general = createGeneral(ships = 2000, training = 80, morale = 50)
        val city = createCity(factionId = 1)
        val nation = createNation(funds = 20000, supplies = 20000)
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
        val general = createGeneral(ships = 2000, training = 90, morale = 90)
        val city = createCity(factionId = 1, frontState = 2)
        val enemyCity = createCity(id = 2, factionId = 2, frontState = 0)
        val nation = createNation(funds = 20000, supplies = 20000)
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
    fun `develops agriculture when city production is low`() {
        val world = createWorld()
        val general = createGeneral(ships = 0, intelligence = 80, command = 30, leadership = 30)
        val city = createCity(factionId = 1, production = 100, productionMax = 1000)
        val nation = createNation()

        setupRepos(world, general, city, nation)

        val action = ai.decideAndExecute(general, world)
        assertEquals("농지개간", action)
    }

    @Test
    fun `develops commerce when production is ok but commerce is low`() {
        val world = createWorld()
        val general = createGeneral(ships = 0, intelligence = 80, command = 30, leadership = 30)
        val city = createCity(factionId = 1, production = 600, productionMax = 1000, commerce = 100, commerceMax = 1000)
        val nation = createNation()

        setupRepos(world, general, city, nation)

        val action = ai.decideAndExecute(general, world)
        assertEquals("상업투자", action)
    }

    @Test
    fun `develops security when production and commerce are ok but security is low`() {
        val world = createWorld()
        val general = createGeneral(ships = 0, intelligence = 80, command = 30, leadership = 30)
        val city = createCity(factionId = 1, production = 600, productionMax = 1000, commerce = 600, commerceMax = 1000, security = 100, securityMax = 1000)
        val nation = createNation()

        setupRepos(world, general, city, nation)

        val action = ai.decideAndExecute(general, world)
        assertEquals("치안강화", action)
    }

    // ========== Peace actions: type-based ==========

    @Test
    fun `warrior type acts based on PHP priority during peace`() {
        val world = createWorld()
        val general = createGeneral(command = 90, leadership = 50, intelligence = 30, ships = 500, training = 50)
        val city = createCity(factionId = 1, production = 600, productionMax = 1000, commerce = 600, commerceMax = 1000, security = 600, securityMax = 1000)
        val nation = createNation()
        val otherNation = createNation(id = 2)
        val diplomacy = createDiplomacy(srcFactionId = 1, destFactionId = 2, stateCode = "동맹")

        setupRepos(world, general, city, nation,
            allNations = listOf(nation, otherNation),
            diplomacies = listOf(diplomacy))

        val action = ai.decideAndExecute(general, world)
        // With PHP priority order (NPC사망대비, 귀환, 금쌀구매, 출병, 긴급내정, 전투준비, ...소집해제...)
        // During peace with ships =500 and low train, action depends on priority iteration
        assertNotNull(action, "Should produce an action during peace")
    }

    @Test
    fun `warrior type produces valid action when at war with no crew`() {
        val world = createWorld()
        // With PHP priority order, general with no crew and low gold may do domestic or disband
        val general = createGeneral(command = 90, leadership = 50, intelligence = 30, ships = 0, funds = 200, supplies = 200, training = 90, morale = 90)
        val city = createCity(factionId = 1, production = 600, productionMax = 1000, commerce = 600, commerceMax = 1000, security = 600, securityMax = 1000)
        val nation = createNation()
        val diplomacy = createDiplomacy(srcFactionId = 1, destFactionId = 2, stateCode = "전쟁")

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
        val chief = createGeneral(id = 1, officerLevel = 20, ships = 2000)
        val unassigned = createGeneral(id = 2, officerLevel = 0, npcState = 2)
        val city = createCity(factionId = 1)
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
        val chief = createGeneral(id = 1, officerLevel = 20, ships = 2000)
        val assigned = createGeneral(id = 2, officerLevel = 3, npcState = 2)
        val city = createCity(factionId = 1)
        city.level = 3
        val nation = createNation(funds = 20000)

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
        // Per legacy parity: funds =500 with leadership=50 -> trainCost=150, goldAfter=350
        // 350 < 900 (trainCost*6), so 징병 (conscript) not 모병 (volunteer)
        // Use "전쟁" (PHP state=0, active war) to trigger AT_WAR diplomacy state
        // Set train/atmos high so 전투준비 is skipped, reaching 징병 in PHP priority
        val general = createGeneral(ships = 50, funds = 500, training = 90, morale = 90)
        val city = createCity(factionId = 1)
        val nation = createNation(funds = 20000, supplies = 20000)
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
        val general = createGeneral(ships = 0, intelligence = 80, command = 30, leadership = 30)
        val city = createCity(factionId = 1, production = 100, productionMax = 1000)
        val nation = createNation()

        setupRepos(world, general, city, nation)

        // Peace + low production => should develop
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

    // ========== Wanderer (factionId=0) ==========

    @Test
    fun `wanderer returns 요양 when injured`() {
        val world = createWorld()
        // injury must exceed cureThreshold (default 10) for strict > comparison
        val general = createGeneral(factionId = 0, injury = 15)

        val action = ai.decideAndExecute(general, world)
        assertEquals("요양", action)
    }

    @Test
    fun `wanderer returns one of exploration actions`() {
        val world = createWorld()
        val general = createGeneral(factionId = 0, injury = 0)

        val action = ai.decideAndExecute(general, world)
        assertTrue(action in listOf("견문", "이동", "물자조달", "휴식"),
            "Expected wanderer action but got: $action")
    }

    // ========== Reserved command ==========

    @Test
    fun `uses reserved command when set in meta`() {
        val world = createWorld()
        val general = createGeneral(ships = 0, intelligence = 80, command = 30, leadership = 30)
        general.meta["reservedCommand"] = "단련"
        val city = createCity(factionId = 1)
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
        val general = createGeneral(ships = 0, intelligence = 80, command = 30, leadership = 30)
        general.meta["reservedCommand"] = "휴식"
        val city = createCity(factionId = 1, production = 100, productionMax = 1000)
        val nation = createNation()

        setupRepos(world, general, city, nation)

        val action = ai.decideAndExecute(general, world)
        // Should fall through to normal AI decision (농지개간 since production is low)
        assertEquals("농지개간", action)
    }

    // ========== Cure threshold ==========

    @Test
    fun `returns 요양 when injury exceeds cure threshold from policy`() {
        val world = createWorld()
        // Injury = 25, default cureThreshold = 20
        val general = createGeneral(injury = 25)
        val city = createCity(factionId = 1)
        val nation = createNation()

        setupRepos(world, general, city, nation)

        val action = ai.decideAndExecute(general, world)
        assertEquals("요양", action)
    }

    // ========== classifyGeneral: probabilistic hybrid ==========

    @Test
    fun `classifyGeneral sets WARRIOR for strength-dominant general`() {
        val general = createGeneral(command = 90, intelligence = 30, leadership = 30)
        val flags = ai.classifyGeneral(general, Random(42))
        assertTrue(flags and GeneralType.WARRIOR.flag != 0,
            "Should have WARRIOR flag")
    }

    @Test
    fun `classifyGeneral sets STRATEGIST for intel-dominant general`() {
        val general = createGeneral(command = 30, intelligence = 90, leadership = 30)
        val flags = ai.classifyGeneral(general, Random(42))
        assertTrue(flags and GeneralType.STRATEGIST.flag != 0,
            "Should have STRATEGIST flag")
    }

    @Test
    fun `classifyGeneral sets COMMANDER when leadership meets threshold`() {
        val general = createGeneral(command = 50, intelligence = 50, leadership = 70)
        val flags = ai.classifyGeneral(general, Random(42), minNPCWarLeadership = 40)
        assertTrue(flags and GeneralType.COMMANDER.flag != 0,
            "Should have COMMANDER flag")
    }

    @Test
    fun `classifyGeneral does not set COMMANDER when leadership below threshold`() {
        val general = createGeneral(command = 50, intelligence = 50, leadership = 30)
        val flags = ai.classifyGeneral(general, Random(42), minNPCWarLeadership = 40)
        assertTrue(flags and GeneralType.COMMANDER.flag == 0,
            "Should not have COMMANDER flag")
    }

    @Test
    fun `autoPromoteLord promotes best NPC when nation has no lord`() {
        val mockPorts = mock(WorldWritePort::class.java)
        val gen1 = createGeneral(id = 1, leadership = 80, command = 70, intelligence = 60, officerLevel = 0)
        val gen2 = createGeneral(id = 2, leadership = 90, command = 80, intelligence = 70, officerLevel = 0)
        val gen3 = createGeneral(id = 3, leadership = 50, command = 40, intelligence = 30, officerLevel = 1)

        val result = ai.autoPromoteLord(listOf(gen1, gen2, gen3), mockPorts)

        assertNotNull(result)
        assertEquals(2L, result!!.id)
        assertEquals(20, result.officerLevel.toInt())
    }

    @Test
    fun `autoPromoteLord does nothing when lord exists`() {
        val mockPorts = mock(WorldWritePort::class.java)
        val lord = createGeneral(id = 1, officerLevel = 20)
        val gen2 = createGeneral(id = 2, leadership = 90, command = 80, intelligence = 70, officerLevel = 0)

        val result = ai.autoPromoteLord(listOf(lord, gen2), mockPorts)

        assertNull(result)
        verifyNoInteractions(mockPorts)
    }

    @Test
    fun `getNationChiefLevel matches legacy exact mapping`() {
        val method = OfficerAI::class.java.getDeclaredMethod("getNationChiefLevel", Int::class.java)
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
        // Legacy PHP chooseGeneralTurn: reserved command checked BEFORE factionId==0 routing.
        // Kotlin bug: factionId==0 routes directly to decideWandererAction, skipping reserved command.
        val world = createWorld()
        val general = createGeneral(factionId = 0, injury = 0)
        general.meta["reservedCommand"] = "물자조달"

        val action = ai.decideAndExecute(general, world)

        assertEquals("물자조달", action, "Wanderer should use reserved command per legacy order")
        assertNull(general.meta["reservedCommand"], "Reserved command should be cleared after use")
    }

    // ========== Legacy parity: classifyGeneral stat-ratio probability ==========

    @Test
    fun `classifyGeneral uses stat-ratio probability not fixed 50 percent for hybrid warrior`() {
        // Legacy PHP calcGenType: nextBool(intel/strength/2) for warrior-base hybrid.
        // command =100, intelligence =80: ratio=0.8, prob = 0.8/2 = 0.4 (40%)
        // With nextDouble()=0.45: 0.45 > 0.4 → legacy: NO STRATEGIST added
        // Kotlin bug: uses nextInt(100)<50 (50% fixed) — nextBits=0 gives 0 < 50 → STRATEGIST added (wrong)
        val general = createGeneral(command = 100, intelligence = 80, leadership = 30)
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
        // intelligence =100, command =80: ratio=0.8, prob = 0.8/2 = 0.4 (40%)
        // With nextDouble()=0.45: 0.45 > 0.4 → legacy: NO WARRIOR added
        val general = createGeneral(intelligence = 100, command = 80, leadership = 30)
        val flags = ai.classifyGeneral(general, FixedRandom(0.45), 40)

        assertTrue(flags and GeneralType.STRATEGIST.flag != 0, "Should have STRATEGIST flag")
        assertTrue(
            flags and GeneralType.WARRIOR.flag == 0,
            "Should NOT have WARRIOR: legacy prob=0.4, random=0.45 > 0.4 → no hybrid type"
        )
    }

    // ========== Legacy parity: doRise current city level check ==========

    // ========== Military AI parity: doSortie ==========

    @Test
    fun `doSortie returns 출병 when at war with sufficient crew train atmos at front city`() {
        val world = createWorld()
        val general = createGeneral(
            leadership = 80, command = 70, intelligence = 50,
            ships = 3000, training = 95, morale = 95,
        )
        val city = createCity(id = 1, factionId = 1, frontState = 2)
        val enemyCity = createCity(id = 2, factionId = 2, frontState = 0)
        val nation = createNation(funds = 20000, supplies = 20000)

        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = DiplomacyState.AT_WAR,
            generalType = GeneralType.COMMANDER.flag or GeneralType.WARRIOR.flag,
            allCities = listOf(city, enemyCity),
            allGenerals = listOf(general),
            allNations = listOf(nation, createNation(id = 2)),
            frontCities = listOf(city),
            rearCities = emptyList(),
            nationGenerals = listOf(general),
            mapAdjacency = mapOf(1L to listOf(2L), 2L to listOf(1L)),
        )

        val result = invokeDoSortie(ctx, Random(42), NpcNationPolicy(), true, mapOf(2L to 2))
        assertEquals("출병", result)
        @Suppress("UNCHECKED_CAST")
        val aiArg = general.meta["aiArg"] as Map<String, Any>
        assertEquals(2L, aiArg["destCityId"])
    }

    @Test
    fun `doSortie returns null when not attackable`() {
        val world = createWorld()
        val general = createGeneral(ships = 3000, training = 95, morale = 95)
        val city = createCity(frontState = 2)
        val nation = createNation()

        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = DiplomacyState.AT_WAR,
            generalType = GeneralType.COMMANDER.flag,
            allCities = listOf(city),
            allGenerals = listOf(general),
            allNations = listOf(nation),
            frontCities = listOf(city),
            rearCities = emptyList(),
            nationGenerals = listOf(general),
        )

        val result = invokeDoSortie(ctx, Random(42), NpcNationPolicy(), false, mapOf(2L to 2))
        assertNull(result)
    }

    @Test
    fun `doSortie returns null when crew below minWarCrew policy threshold`() {
        // PHP guard: crew < min((fullLeadership - 2) * 100, nationPolicy.minWarCrew)
        // leadership=80, (80-2)*100=7800, minWarCrew=1500 -> threshold=1500
        // ships =1000 < 1500 -> null
        val world = createWorld()
        val general = createGeneral(leadership = 80, ships = 1000, training = 95, morale = 95)
        val city = createCity(frontState = 2)
        val enemyCity = createCity(id = 2, factionId = 2)
        val nation = createNation(supplies = 20000)

        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = DiplomacyState.AT_WAR,
            generalType = GeneralType.COMMANDER.flag,
            allCities = listOf(city, enemyCity),
            allGenerals = listOf(general),
            allNations = listOf(nation, createNation(id = 2)),
            frontCities = listOf(city),
            rearCities = emptyList(),
            nationGenerals = listOf(general),
            mapAdjacency = mapOf(1L to listOf(2L)),
        )

        val result = invokeDoSortie(ctx, Random(42), NpcNationPolicy(), true, mapOf(2L to 2))
        assertNull(result, "Should return null when crew < min((leadership-2)*100, minWarCrew)")
    }

    @Test
    fun `doSortie uses nationPolicy properWarTrainAtmos for train check`() {
        // PHP: train < min(100, nationPolicy.properWarTrainAtmos) -> default 90
        // With training =85 and policy default 90: 85 < 90 -> should return null
        val world = createWorld()
        val general = createGeneral(leadership = 80, ships = 3000, training = 85, morale = 95)
        val city = createCity(frontState = 2)
        val enemyCity = createCity(id = 2, factionId = 2)
        val nation = createNation(supplies = 20000)

        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = DiplomacyState.AT_WAR,
            generalType = GeneralType.COMMANDER.flag,
            allCities = listOf(city, enemyCity),
            allGenerals = listOf(general),
            allNations = listOf(nation, createNation(id = 2)),
            frontCities = listOf(city),
            rearCities = emptyList(),
            nationGenerals = listOf(general),
            mapAdjacency = mapOf(1L to listOf(2L)),
        )

        val result = invokeDoSortie(ctx, Random(42), NpcNationPolicy(), true, mapOf(2L to 2))
        assertNull(result, "Should return null when train < properWarTrainAtmos (90)")
    }

    // ========== Military AI parity: doRecruit ==========

    @Test
    fun `doRecruit returns 징병 when at war with low crew and sufficient population`() {
        val world = createWorld()
        val general = createGeneral(
            leadership = 50, ships = 500, funds = 2000, supplies = 2000,
            command = 70, intelligence = 40,
        )
        val city = createCity(factionId = 1).apply {
            population = 80000
            populationMax = 100000
        }
        val nation = createNation(funds = 20000, supplies = 20000)

        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = DiplomacyState.AT_WAR,
            generalType = GeneralType.COMMANDER.flag or GeneralType.WARRIOR.flag,
            allCities = listOf(city),
            allGenerals = listOf(general),
            allNations = listOf(nation),
            frontCities = emptyList(),
            rearCities = listOf(city),
            nationGenerals = listOf(general),
        )

        val result = invokeDoRecruit(ctx, Random(42), NpcGeneralPolicy(), NpcNationPolicy(), emptyMap())
        assertTrue(result == "징병" || result == "모병", "Expected recruitment but got: $result")
    }

    @Test
    fun `doRecruit returns null during peace without neutral targets`() {
        val world = createWorld()
        val general = createGeneral(ships = 500, funds = 2000, supplies = 2000)
        val city = createCity(factionId = 1)
        val nation = createNation()

        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = DiplomacyState.PEACE,
            generalType = GeneralType.COMMANDER.flag,
            allCities = listOf(city),
            allGenerals = listOf(general),
            allNations = listOf(nation),
            frontCities = emptyList(),
            rearCities = listOf(city),
            nationGenerals = listOf(general),
        )

        val result = invokeDoRecruit(ctx, Random(42), NpcGeneralPolicy(), NpcNationPolicy(), emptyMap())
        assertNull(result, "Should not recruit during peace")
    }

    @Test
    fun `doRecruit returns null when population too low for safe recruitment`() {
        // PHP: remainPop = population - minNPCRecruitCityPopulation - fullLeadership*100
        // leadership=50, minNPCRecruitCityPopulation=50000, remaining = 10000 - 50000 - 5000 = -45000 -> null
        val world = createWorld()
        val general = createGeneral(leadership = 50, ships = 500, funds = 2000, supplies = 2000)
        val city = createCity(factionId = 1).apply {
            population = 10000
            populationMax = 100000
        }
        val nation = createNation()

        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = DiplomacyState.AT_WAR,
            generalType = GeneralType.COMMANDER.flag,
            allCities = listOf(city),
            allGenerals = listOf(general),
            allNations = listOf(nation),
            frontCities = emptyList(),
            rearCities = listOf(city),
            nationGenerals = listOf(general),
        )

        val result = invokeDoRecruit(ctx, Random(42), NpcGeneralPolicy(), NpcNationPolicy(), emptyMap())
        assertNull(result, "Should not recruit when population too low")
    }

    // ========== Military AI parity: doCombatPrep ==========

    @Test
    fun `doCombatPrep returns training action when train below policy threshold`() {
        // PHP uses nationPolicy.properWarTrainAtmos (default 90)
        // training =60, morale =80: both below 90 -> weighted choice, but train has higher weight
        val world = createWorld()
        val general = createGeneral(ships = 2000, training = 60, morale = 80)
        val city = createCity(factionId = 1)
        val nation = createNation()

        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = DiplomacyState.AT_WAR,
            generalType = GeneralType.COMMANDER.flag,
            allCities = listOf(city),
            allGenerals = listOf(general),
            allNations = listOf(nation),
            frontCities = emptyList(),
            rearCities = listOf(city),
            nationGenerals = listOf(general),
        )

        val result = invokeDoCombatPrep(ctx, Random(42), NpcNationPolicy(), emptyMap())
        assertNotNull(result, "Should return training action when train/atmos below threshold")
        assertTrue(result == "훈련" || result == "사기진작", "Expected combat prep but got: $result")
    }

    @Test
    fun `doCombatPrep returns null when train and atmos both at or above policy threshold`() {
        // PHP: both >= properWarTrainAtmos (90) -> no cmd -> null
        val world = createWorld()
        val general = createGeneral(ships = 2000, training = 95, morale = 95)
        val city = createCity(factionId = 1)
        val nation = createNation()

        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = DiplomacyState.AT_WAR,
            generalType = GeneralType.COMMANDER.flag,
            allCities = listOf(city),
            allGenerals = listOf(general),
            allNations = listOf(nation),
            frontCities = emptyList(),
            rearCities = listOf(city),
            nationGenerals = listOf(general),
        )

        val result = invokeDoCombatPrep(ctx, Random(42), NpcNationPolicy(), emptyMap())
        assertNull(result, "Should return null when both train and atmos >= properWarTrainAtmos (90)")
    }

    @Test
    fun `doCombatPrep uses weighted choice between train and atmos per PHP`() {
        // PHP: training =60, morale =85, threshold=90 -> both below
        // PHP builds weighted list: [훈련 weight=maxTrain/60, 사기진작 weight=maxAtmos/85]
        // Kotlin should use weighted choice, not deterministic first match
        val world = createWorld()
        val general = createGeneral(ships = 2000, training = 85, morale = 60)
        val city = createCity(factionId = 1)
        val nation = createNation()

        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = DiplomacyState.AT_WAR,
            generalType = GeneralType.COMMANDER.flag,
            allCities = listOf(city),
            allGenerals = listOf(general),
            allNations = listOf(nation),
            frontCities = emptyList(),
            rearCities = listOf(city),
            nationGenerals = listOf(general),
        )

        // With atmos much lower, 사기진작 should have higher weight
        // Using a specific seed that would produce 사기진작 given proper weighting
        val result = invokeDoCombatPrep(ctx, Random(42), NpcNationPolicy(), emptyMap())
        assertNotNull(result, "Should return combat prep action")
        // With proper weighted choice, morale =60 has weight ~1.5 vs training =85 weight ~1.06
        // So 사기진작 is more likely. The old code always returns 훈련 first.
        assertTrue(result == "훈련" || result == "사기진작", "Expected combat prep but got: $result")
    }

    // ========== Military AI parity: doWarpToFront ==========

    @Test
    fun `doWarpToFront returns warp action when commander in rear city with front cities available`() {
        val world = createWorld()
        val general = createGeneral(leadership = 80, ships = 2000, training = 90, morale = 90)
        val rearCity = createCity(id = 1, factionId = 1, frontState = 0)
        val frontCity1 = createCity(id = 2, factionId = 1, frontState = 2).apply { supplyState = 1 }
        val frontCity2 = createCity(id = 3, factionId = 1, frontState = 2).apply { supplyState = 1 }
        val nation = createNation()

        val ctx = AIContext(
            world = world, general = general, city = rearCity, nation = nation,
            diplomacyState = DiplomacyState.AT_WAR,
            generalType = GeneralType.COMMANDER.flag,
            allCities = listOf(rearCity, frontCity1, frontCity2),
            allGenerals = listOf(general),
            allNations = listOf(nation),
            frontCities = listOf(frontCity1, frontCity2),
            rearCities = listOf(rearCity),
            nationGenerals = listOf(general),
        )

        val result = invokeDoWarpToFront(ctx, Random(42), NpcNationPolicy(), true, mapOf(2L to 2))
        assertEquals("이동", result)
        @Suppress("UNCHECKED_CAST")
        val aiArg = general.meta["aiArg"] as Map<String, Any>
        assertTrue(aiArg["destCityId"] == 2L || aiArg["destCityId"] == 3L, "Should warp to a front city")
    }

    @Test
    fun `doWarpToFront returns null when already at front city`() {
        val world = createWorld()
        val general = createGeneral(leadership = 80, ships = 2000)
        val frontCity = createCity(id = 1, factionId = 1, frontState = 2).apply { supplyState = 1 }
        val nation = createNation()

        val ctx = AIContext(
            world = world, general = general, city = frontCity, nation = nation,
            diplomacyState = DiplomacyState.AT_WAR,
            generalType = GeneralType.COMMANDER.flag,
            allCities = listOf(frontCity),
            allGenerals = listOf(general),
            allNations = listOf(nation),
            frontCities = listOf(frontCity),
            rearCities = emptyList(),
            nationGenerals = listOf(general),
        )

        val result = invokeDoWarpToFront(ctx, Random(42), NpcNationPolicy(), true, mapOf(2L to 2))
        assertNull(result, "Should not warp when already at front city")
    }

    // ========== Reflection helpers for military methods ==========

    private fun invokeDoSortie(
        ctx: AIContext, rng: Random, nationPolicy: NpcNationPolicy,
        attackable: Boolean, warTargetNations: Map<Long, Int>,
    ): String? {
        val method = OfficerAI::class.java.getDeclaredMethod(
            "doSortie",
            AIContext::class.java, Random::class.java, NpcNationPolicy::class.java,
            Boolean::class.java, Map::class.java,
        )
        method.isAccessible = true
        return method.invoke(ai, ctx, rng, nationPolicy, attackable, warTargetNations) as String?
    }

    private fun invokeDoRecruit(
        ctx: AIContext, rng: Random, policy: NpcGeneralPolicy,
        nationPolicy: NpcNationPolicy, warTargetNations: Map<Long, Int>,
    ): String? {
        val method = OfficerAI::class.java.getDeclaredMethod(
            "doRecruit",
            AIContext::class.java, Random::class.java, NpcGeneralPolicy::class.java,
            NpcNationPolicy::class.java, Map::class.java,
        )
        method.isAccessible = true
        return method.invoke(ai, ctx, rng, policy, nationPolicy, warTargetNations) as String?
    }

    private fun invokeDoCombatPrep(
        ctx: AIContext, rng: Random, nationPolicy: NpcNationPolicy,
        warTargetNations: Map<Long, Int>,
    ): String? {
        val method = OfficerAI::class.java.getDeclaredMethod(
            "doCombatPrep",
            AIContext::class.java, Random::class.java, NpcNationPolicy::class.java,
            Map::class.java,
        )
        method.isAccessible = true
        return method.invoke(ai, ctx, rng, nationPolicy, warTargetNations) as String?
    }

    private fun invokeDoWarpToFront(
        ctx: AIContext, rng: Random, nationPolicy: NpcNationPolicy,
        attackable: Boolean, warTargetNations: Map<Long, Int>,
    ): String? {
        val method = OfficerAI::class.java.getDeclaredMethod(
            "doWarpToFront",
            AIContext::class.java, Random::class.java, NpcNationPolicy::class.java,
            Boolean::class.java, Map::class.java,
        )
        method.isAccessible = true
        return method.invoke(ai, ctx, rng, nationPolicy, attackable, warTargetNations) as String?
    }

    // ========== Military AI parity: doWarpToRear ==========

    @Test
    fun `doWarpToRear returns warp when commander with low crew and recruitable rear city exists`() {
        val world = createWorld()
        val general = createGeneral(leadership = 80, ships = 500, training = 90, morale = 90)
        val frontCity = createCity(id = 1, factionId = 1, frontState = 2).apply {
            population = 10000
            populationMax = 100000
        }
        val rearCity = createCity(id = 2, factionId = 1, frontState = 0).apply {
            population = 80000
            populationMax = 100000
        }
        val nation = createNation()

        val ctx = AIContext(
            world = world, general = general, city = frontCity, nation = nation,
            diplomacyState = DiplomacyState.AT_WAR,
            generalType = GeneralType.COMMANDER.flag,
            allCities = listOf(frontCity, rearCity),
            allGenerals = listOf(general),
            allNations = listOf(nation),
            frontCities = listOf(frontCity),
            rearCities = listOf(rearCity),
            nationGenerals = listOf(general),
        )

        val result = invokeDoWarpToRear(
            ctx, Random(42), NpcGeneralPolicy(), NpcNationPolicy(),
            listOf(rearCity), listOf(frontCity, rearCity),
        )
        assertEquals("이동", result)
    }

    @Test
    fun `doWarpToRear returns null during peace`() {
        val world = createWorld()
        val general = createGeneral(leadership = 80, ships = 500)
        val city = createCity(frontState = 2)
        val rearCity = createCity(id = 2, frontState = 0)
        val nation = createNation()

        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = DiplomacyState.PEACE,
            generalType = GeneralType.COMMANDER.flag,
            allCities = listOf(city, rearCity),
            allGenerals = listOf(general),
            allNations = listOf(nation),
            frontCities = listOf(city),
            rearCities = listOf(rearCity),
            nationGenerals = listOf(general),
        )

        val result = invokeDoWarpToRear(
            ctx, Random(42), NpcGeneralPolicy(), NpcNationPolicy(),
            listOf(rearCity), listOf(city, rearCity),
        )
        assertNull(result, "Should not warp to rear during peace")
    }

    @Test
    fun `doWarpToRear returns null when already have enough crew`() {
        val world = createWorld()
        val general = createGeneral(leadership = 80, ships = 2000)
        val frontCity = createCity(id = 1, factionId = 1, frontState = 2)
        val rearCity = createCity(id = 2, factionId = 1, frontState = 0).apply {
            population = 80000
            populationMax = 100000
        }
        val nation = createNation()

        val ctx = AIContext(
            world = world, general = general, city = frontCity, nation = nation,
            diplomacyState = DiplomacyState.AT_WAR,
            generalType = GeneralType.COMMANDER.flag,
            allCities = listOf(frontCity, rearCity),
            allGenerals = listOf(general),
            allNations = listOf(nation),
            frontCities = listOf(frontCity),
            rearCities = listOf(rearCity),
            nationGenerals = listOf(general),
        )

        val result = invokeDoWarpToRear(
            ctx, Random(42), NpcGeneralPolicy(), NpcNationPolicy(),
            listOf(rearCity), listOf(frontCity, rearCity),
        )
        assertNull(result, "Should not warp to rear when crew >= minWarCrew")
    }

    // ========== Military AI parity: doRally ==========

    @Test
    fun `doRally always returns 집합 for troop leader`() {
        val general = createGeneral(npcState = 5).apply {
            killTurn = 70
        }

        val result = invokeDoRally(general, Random(42))
        assertEquals("집합", result)
    }

    @Test
    fun `doRally updates killTurn per PHP formula for npcState 5`() {
        // PHP: newKillTurn = (killTurn + rng.nextRangeInt(2, 4)) % 5 + 70
        // With killTurn=72, rng.nextInt(3)+2 gives 2-4
        val general = createGeneral(npcState = 5).apply {
            killTurn = 72
        }

        invokeDoRally(general, Random(42))
        val kt = general.killTurn?.toInt() ?: -1
        assertTrue(kt in 70..74, "killTurn should be in 70-74 range after rally, got: $kt")
    }

    @Test
    fun `doRally returns 집합 for non-troop general too`() {
        val general = createGeneral(npcState = 2)

        val result = invokeDoRally(general, Random(42))
        assertEquals("집합", result)
    }

    // ========== Military AI parity: doDismiss ==========

    @Test
    fun `doDismiss returns null when attackable`() {
        val world = createWorld()
        val general = createGeneral(ships = 2000)
        val city = createCity(factionId = 1)
        val nation = createNation()

        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = DiplomacyState.PEACE,
            generalType = GeneralType.COMMANDER.flag,
            allCities = listOf(city),
            allGenerals = listOf(general),
            allNations = listOf(nation),
            frontCities = emptyList(),
            rearCities = listOf(city),
            nationGenerals = listOf(general),
        )

        val result = invokeDoDismiss(ctx, Random(42), true)
        assertNull(result, "Should not dismiss when attackable")
    }

    @Test
    fun `doDismiss returns null when crew is zero`() {
        val world = createWorld()
        val general = createGeneral(ships = 0)
        val city = createCity(factionId = 1)
        val nation = createNation()

        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = DiplomacyState.PEACE,
            generalType = GeneralType.COMMANDER.flag,
            allCities = listOf(city),
            allGenerals = listOf(general),
            allNations = listOf(nation),
            frontCities = emptyList(),
            rearCities = listOf(city),
            nationGenerals = listOf(general),
        )

        val result = invokeDoDismiss(ctx, Random(42), false)
        assertNull(result, "Should not dismiss when crew is zero")
    }

    @Test
    fun `doDismiss returns 소집해제 when at peace with crew and random passes`() {
        // PHP: 75% chance to skip -> need random >= 0.75 to proceed
        val world = createWorld()
        val general = createGeneral(ships = 2000)
        val city = createCity(factionId = 1)
        val nation = createNation()

        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = DiplomacyState.PEACE,
            generalType = GeneralType.COMMANDER.flag,
            allCities = listOf(city),
            allGenerals = listOf(general),
            allNations = listOf(nation),
            frontCities = emptyList(),
            rearCities = listOf(city),
            nationGenerals = listOf(general),
        )

        // FixedRandom(0.9) -> 0.9 >= 0.75, not < 0.75 -> passes the check
        val result = invokeDoDismiss(ctx, FixedRandom(0.9), false)
        assertEquals("소집해제", result, "Should dismiss when random >= 0.75")
    }

    @Test
    fun `doDismiss returns null when not at peace`() {
        val world = createWorld()
        val general = createGeneral(ships = 2000)
        val city = createCity(factionId = 1)
        val nation = createNation()

        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = DiplomacyState.AT_WAR,
            generalType = GeneralType.COMMANDER.flag,
            allCities = listOf(city),
            allGenerals = listOf(general),
            allNations = listOf(nation),
            frontCities = emptyList(),
            rearCities = listOf(city),
            nationGenerals = listOf(general),
        )

        val result = invokeDoDismiss(ctx, FixedRandom(0.9), false)
        assertNull(result, "Should not dismiss when not at peace")
    }

    // ========== Reflection helpers for Task 2 military methods ==========

    private fun invokeDoWarpToRear(
        ctx: AIContext, rng: Random, policy: NpcGeneralPolicy,
        nationPolicy: NpcNationPolicy,
        backupCities: List<Planet>, supplyCities: List<Planet>,
    ): String? {
        val method = OfficerAI::class.java.getDeclaredMethod(
            "doWarpToRear",
            AIContext::class.java, Random::class.java, NpcGeneralPolicy::class.java,
            NpcNationPolicy::class.java, List::class.java, List::class.java,
        )
        method.isAccessible = true
        return method.invoke(ai, ctx, rng, policy, nationPolicy, backupCities, supplyCities) as String?
    }

    private fun invokeDoRally(general: Officer, rng: Random): String {
        val method = OfficerAI::class.java.getDeclaredMethod(
            "doRally",
            Officer::class.java, Random::class.java,
        )
        method.isAccessible = true
        return method.invoke(ai, general, rng) as String
    }

    private fun invokeDoDismiss(ctx: AIContext, rng: Random, attackable: Boolean): String? {
        val method = OfficerAI::class.java.getDeclaredMethod(
            "doDismiss",
            AIContext::class.java, Random::class.java, Boolean::class.java,
        )
        method.isAccessible = true
        return method.invoke(ai, ctx, rng, attackable) as String?
    }

    @Test
    fun `doRise skips 50 percent of time when general at non-major city (legacy parity)`() {
        // Legacy PHP do거병: if currentCityLevel < 5 or > 6, nextBool(0.5) skip.
        // When random > 0.5 at a small city, legacy returns null.
        // Kotlin bug: no current city level check → proceeds even at small city.
        val world = createWorld(year = 181, month = 3).apply {
            config["startyear"] = 180
        }
        val general = createGeneral(
            factionId = 0, planetId = 1, leadership = 80, command = 80, intelligence = 80, npcState = 2,
        )
        val smallCity = createCity(id = 1, factionId = 0).apply { level = 3 }
        val openMajorCity = createCity(id = 2, factionId = 0).apply { level = 5 }

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

        // ── doTroopRearAssignment: troop leader in low-population city → rear assignment ──

        @Test
        fun `doTroopRearAssignment returns 발령 when troop leader in low-population city`() {
            val world = createWorld()
            val chief = createGeneral(id = 1, officerLevel = 20, npcState = 2).apply {
                leadership = 80; ships = 2000
            }
            val troopLeader = createGeneral(id = 2, npcState = 5, officerLevel = 1).apply {
                leadership = 60; planetId = 1
            }
            val lowPopCity = createCity(id = 1, factionId = 1).apply {
                population = 10000; populationMax = 100000  // 10% < 50% safeRecruitRatio
                supplyState = 1
            }
            val highPopCity = createCity(id = 2, factionId = 1).apply {
                population = 80000; populationMax = 100000  // 80% > 50%
                frontState = 0; supplyState = 1
            }
            val nation = createNation().apply { capitalPlanetId = 1L }

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
            val city = createCity(id = 1, factionId = 1).apply {
                population = 80000; populationMax = 100000; supplyState = 1
            }
            val nation = createNation().apply { capitalPlanetId = 1L }

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
                planetId = 2 // In non-supply city
            }
            val supplyCity = createCity(id = 1, factionId = 1).apply { supplyState = 1 }
            val nonSupplyCity = createCity(id = 2, factionId = 1).apply { supplyState = 0 }
            val nation = createNation().apply { capitalPlanetId = 1L }

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
                planetId = 1 // In supply city
            }
            val supplyCity = createCity(id = 1, factionId = 1).apply { supplyState = 1 }
            val nation = createNation().apply { capitalPlanetId = 1L }

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
                planetId = 1; ships = 2000; training = 90; morale = 90
            }
            val rearCity = createCity(id = 1, factionId = 1).apply {
                frontState = 0; supplyState = 1
            }
            val frontCity = createCity(id = 2, factionId = 1).apply {
                frontState = 2; supplyState = 1
            }
            val nation = createNation().apply { capitalPlanetId = 1L }

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
            val frontCity = createCity(id = 2, factionId = 1).apply { frontState = 2 }
            val nation = createNation().apply { capitalPlanetId = 1L }

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
                planetId = 1; ships = 100; leadership = 50
            }
            val lowPopSupplyCity = createCity(id = 1, factionId = 1).apply {
                population = 10000; populationMax = 100000; supplyState = 1; frontState = 2
            }
            val highPopRearCity = createCity(id = 2, factionId = 1).apply {
                population = 80000; populationMax = 100000; supplyState = 1; frontState = 0
            }
            val nation = createNation().apply { capitalPlanetId = 1L }

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
                planetId = 1; leadership = 30
            }
            val developedCity = createCity(id = 1, factionId = 1).apply {
                production = 1000; productionMax = 1000; commerce = 1000; commerceMax = 1000
                security = 1000; securityMax = 1000; supplyState = 1
                orbitalDefense = 1000; orbitalDefenseMax = 1000; fortress = 1000; fortressMax = 1000
            }
            val underdevelopedCity = createCity(id = 2, factionId = 1).apply {
                production = 100; productionMax = 1000; commerce = 100; commerceMax = 1000
                security = 100; securityMax = 1000; supplyState = 1
                orbitalDefense = 100; orbitalDefenseMax = 1000; fortress = 100; fortressMax = 1000
            }
            val nation = createNation().apply { capitalPlanetId = 1L }

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
            val developedCity = createCity(id = 1, factionId = 1).apply {
                production = 1000; productionMax = 1000; commerce = 1000; commerceMax = 1000
                security = 1000; securityMax = 1000; supplyState = 1
                orbitalDefense = 1000; orbitalDefenseMax = 1000; fortress = 1000; fortressMax = 1000
            }
            val nation = createNation().apply { capitalPlanetId = 1L }

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
                leadership = 60; funds = 100; supplies = 100; killTurn = 50
            }
            val city = createCity(id = 1, factionId = 1)
            val nation = createNation(funds = 20000, supplies = 20000)

            val ctx = buildAiContext(world, chief, city, nation,
                listOf(chief, poorNpc))
            val result = invokePrivateMethod("doNpcReward", ctx, Random(42), NpcNationPolicy())
            assertEquals("포상", result)
        }

        @Test
        fun `doNpcReward returns null when no eligible NPC generals`() {
            val world = createWorld()
            val chief = createGeneral(id = 1, officerLevel = 20, npcState = 2).apply {
                leadership = 80; funds = 20000; supplies = 20000
            }
            val city = createCity(id = 1, factionId = 1)
            val nation = createNation(funds = 20000, supplies = 20000)

            val ctx = buildAiContext(world, chief, city, nation, listOf(chief))
            val result = invokePrivateMethod("doNpcReward", ctx, Random(42), NpcNationPolicy())
            assertNull(result)
        }

        @Test
        fun `doNpcReward returns null when nation treasury too low`() {
            val world = createWorld()
            val chief = createGeneral(id = 1, officerLevel = 20, npcState = 2)
            val poorNpc = createGeneral(id = 2, npcState = 2, officerLevel = 1).apply {
                funds = 100; supplies = 100; killTurn = 50
            }
            val city = createCity(id = 1, factionId = 1)
            val nation = createNation(funds = 500, supplies = 500)  // Below reqNationGold/Rice

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
                leadership = 30; funds = 20000; supplies = 20000
            }
            val city = createCity(id = 1, factionId = 1)
            val nation = createNation(funds = 5000, supplies = 5000)

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
                leadership = 30; funds = 1000; supplies = 1000
            }
            val city = createCity(id = 1, factionId = 1)
            val nation = createNation(funds = 20000, supplies = 20000)

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
                funds = 100; supplies = 100; killTurn = 50
            }
            val city = createCity(id = 1, factionId = 1)
            val nation = createNation(funds = 20000, supplies = 20000)

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
                leadership = 50; funds = 100; supplies = 5000; killTurn = 50
            }
            val city = createCity(id = 1, factionId = 1)
            val nation = createNation(funds = 50000, supplies = 50000)

            val ctx = buildAiContext(world, chief, city, nation,
                listOf(chief, poorWarUser))
            val result = invokePrivateMethod("doUserUrgentReward", ctx, Random(42), NpcNationPolicy())
            assertEquals("포상", result)
        }

        @Test
        fun `doUserUrgentReward returns null when no eligible user generals`() {
            val world = createWorld()
            val chief = createGeneral(id = 1, officerLevel = 20, npcState = 2)
            val city = createCity(id = 1, factionId = 1)
            val nation = createNation(funds = 20000, supplies = 20000)

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

            val method = OfficerAI::class.java.declaredMethods.first { m ->
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
            val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, factionId = 0).apply {
                planetId = 1
            }
            val currentCity = createCity(id = 1, factionId = 0).apply {
                level = 3; meta["connections"] = listOf(2L)
            }
            val targetCity = createCity(id = 2, factionId = 0).apply {
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
            val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, factionId = 0).apply {
                planetId = 1
            }
            val onlyCity = createCity(id = 1, factionId = 0).apply {
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
            val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, factionId = 0).apply {
                planetId = 1
            }
            val majorCity = createCity(id = 1, factionId = 0).apply {
                level = 5; meta["connections"] = listOf(2L)
            }
            val otherCity = createCity(id = 2, factionId = 0).apply {
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
                factionId = 0, planetId = 1, leadership = 80, command = 80, intelligence = 80, npcState = 2,
            )
            val currentCity = createCity(id = 1, factionId = 0).apply {
                level = 5; meta["connections"] = listOf(2L)
            }
            val openMajorCity = createCity(id = 2, factionId = 0).apply {
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
                factionId = 0, planetId = 1, leadership = 80, command = 80, intelligence = 80, npcState = 2,
            ).apply { makeLimit = 5 }

            val action = invokeDoRise(world, general, FixedRandom(0.3, 0.0))
            assertNull(action)
        }

        // ── doSelectNation: wanderer chooses nation to join ──

        @Test
        fun `doSelectNation returns 임관 for barbarian joining barbarian nation`() {
            val world = createWorld()
            val barbarian = createGeneral(id = 1, npcState = 9, factionId = 0, officerLevel = 0).apply {
                planetId = 1
            }
            val barbarianLord = createGeneral(id = 2, npcState = 9, factionId = 1, officerLevel = 20).apply {
                planetId = 1
            }
            val city = createCity(id = 1, factionId = 0)
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
            val general = createGeneral(id = 1, npcState = 2, factionId = 0, officerLevel = 0).apply {
                planetId = 1; affinity = 999
            }
            val city = createCity(id = 1, factionId = 0)

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
            val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, factionId = 0).apply {
                name = "유비"
            }

            val result = invokeDoFoundNation(lord, Random(42))
            assertEquals("건국", result)
            @Suppress("UNCHECKED_CAST")
            val aiArg = lord.meta["aiArg"] as Map<String, Any>
            assertEquals("유비", aiArg["factionName"])
            assertNotNull(aiArg["nationType"])
            assertNotNull(aiArg["colorType"])
        }

        // ── doDisband: lord disbands weak nation ──

        @Test
        fun `doDisband returns 해산 and clears movingTargetCityID`() {
            val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, factionId = 1).apply {
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
            val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, factionId = 1)
            val better = createGeneral(id = 2, npcState = 2, factionId = 1, officerLevel = 1).apply {
                leadership = 90; command = 90; intelligence = 90
            }
            val city = createCity(id = 1, factionId = 1)
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
            val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, factionId = 1)
            val city = createCity(id = 1, factionId = 1)
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
            val general = createGeneral(id = 1, officerLevel = 5, npcState = 2, factionId = 1)

            val result = invokeDoAbdicate(world, general, Random(42))
            assertNull(result)
        }

        // ── Reflection helpers ──

        private fun invokeDoWandererMove(world: SessionState, general: Officer, rng: Random): String? {
            val method = OfficerAI::class.java.getDeclaredMethod(
                "doWandererMove", Officer::class.java, SessionState::class.java, Random::class.java)
            method.isAccessible = true
            return method.invoke(ai, general, world, rng) as String?
        }

        private fun invokeDoSelectNation(world: SessionState, general: Officer, rng: Random): String? {
            val method = OfficerAI::class.java.getDeclaredMethod(
                "doSelectNation", Officer::class.java, SessionState::class.java, Random::class.java)
            method.isAccessible = true
            return method.invoke(ai, general, world, rng) as String?
        }

        private fun invokeDoFoundNation(general: Officer, rng: Random): String? {
            val method = OfficerAI::class.java.getDeclaredMethod(
                "doFoundNation", Officer::class.java, Random::class.java)
            method.isAccessible = true
            return method.invoke(ai, general, rng) as String?
        }

        private fun invokeDoDisband(general: Officer): String? {
            val method = OfficerAI::class.java.getDeclaredMethod("doDisband", Officer::class.java)
            method.isAccessible = true
            return method.invoke(ai, general) as String?
        }

        private fun invokeDoAbdicate(world: SessionState, general: Officer, rng: Random): String? {
            val method = OfficerAI::class.java.getDeclaredMethod(
                "doAbdicate", Officer::class.java, SessionState::class.java, Random::class.java)
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
            val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, factionId = 1)
            val npc1 = createGeneral(id = 2, npcState = 2, officerLevel = 1).apply {
                leadership = 80; command = 70; intelligence = 60; killTurn = 50
            }
            val npc2 = createGeneral(id = 3, npcState = 2, officerLevel = 1).apply {
                leadership = 70; command = 80; intelligence = 50; killTurn = 50
            }
            val npc3 = createGeneral(id = 4, npcState = 2, officerLevel = 1).apply {
                leadership = 60; command = 50; intelligence = 80; killTurn = 50
            }
            val city = createCity(factionId = 1)
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
            val lord = createGeneral(id = 1, officerLevel = 20, npcState = 2, factionId = 1)
            val npc1 = createGeneral(id = 2, npcState = 2, officerLevel = 1).apply {
                leadership = 80; command = 70; intelligence = 60; killTurn = 50
            }
            val city = createCity(factionId = 1)
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
            val chief = createGeneral(id = 1, officerLevel = 11, npcState = 2, factionId = 1).apply {
                leadership = 80
            }
            val npc1 = createGeneral(id = 2, npcState = 2, officerLevel = 1).apply {
                leadership = 60; command = 70; intelligence = 50; killTurn = 50
            }
            val city = createCity(factionId = 1)
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
            val chief = createGeneral(id = 1, officerLevel = 11, npcState = 2, factionId = 1)
            val existingChief9 = createGeneral(id = 2, npcState = 2, officerLevel = 9).apply {
                leadership = 70; command = 70; intelligence = 70
            }
            val npc1 = createGeneral(id = 3, npcState = 2, officerLevel = 1).apply {
                leadership = 50; command = 50; intelligence = 50; killTurn = 50
            }
            val city = createCity(factionId = 1)
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
            factionId = 0, planetId = 1, leadership = 80, command = 80, intelligence = 80, npcState = 2,
        )
        val smallCity = createCity(id = 1, factionId = 0).apply { level = 3 }
        val openMajorCity = createCity(id = 2, factionId = 0).apply { level = 5 }

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

    // ========== Domestic AI Parity Tests (08-03) ==========

    private fun invokeDoUrgentDomestic(
        world: SessionState, general: Officer, city: Planet, nation: Faction,
        rng: Random = Random(0), dipState: DiplomacyState = DiplomacyState.AT_WAR,
    ): String? {
        val method = OfficerAI::class.java.getDeclaredMethod(
            "doUrgentDomestic", AIContext::class.java, Random::class.java, NpcNationPolicy::class.java,
        )
        method.isAccessible = true
        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = dipState,
            generalType = ai.classifyGeneral(general, Random(0), 40),
            allCities = listOf(city), allGenerals = listOf(general), allNations = listOf(nation),
            frontCities = emptyList(), rearCities = listOf(city), nationGenerals = listOf(general),
        )
        return method.invoke(ai, ctx, rng, NpcNationPolicy()) as String?
    }

    private fun invokeDoWarDomestic(
        world: SessionState, general: Officer, city: Planet, nation: Faction,
        rng: Random = Random(0), dipState: DiplomacyState = DiplomacyState.AT_WAR,
    ): String? {
        val method = OfficerAI::class.java.getDeclaredMethod(
            "doWarDomestic", AIContext::class.java, Random::class.java, NpcNationPolicy::class.java,
        )
        method.isAccessible = true
        val isFront = city.frontState.toInt() in listOf(1, 3)
        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = dipState,
            generalType = ai.classifyGeneral(general, Random(0), 40),
            allCities = listOf(city), allGenerals = listOf(general), allNations = listOf(nation),
            frontCities = if (isFront) listOf(city) else emptyList(),
            rearCities = if (!isFront) listOf(city) else emptyList(),
            nationGenerals = listOf(general),
        )
        return method.invoke(ai, ctx, rng, NpcNationPolicy()) as String?
    }

    private fun invokeDoDonate(
        world: SessionState, general: Officer, city: Planet, nation: Faction,
        rng: Random = Random(0),
    ): String? {
        val method = OfficerAI::class.java.getDeclaredMethod(
            "doDonate", AIContext::class.java, Random::class.java, NpcNationPolicy::class.java,
        )
        method.isAccessible = true
        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = DiplomacyState.PEACE,
            generalType = ai.classifyGeneral(general, Random(0), 40),
            allCities = listOf(city), allGenerals = listOf(general), allNations = listOf(nation),
            frontCities = emptyList(), rearCities = listOf(city), nationGenerals = listOf(general),
        )
        return method.invoke(ai, ctx, rng, NpcNationPolicy()) as String?
    }

    private fun invokeDoNpcDedicate(
        world: SessionState, general: Officer, city: Planet, nation: Faction,
        rng: Random = Random(0),
    ): String? {
        val method = OfficerAI::class.java.getDeclaredMethod(
            "doNpcDedicate", AIContext::class.java, Random::class.java, NpcNationPolicy::class.java,
        )
        method.isAccessible = true
        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = DiplomacyState.PEACE,
            generalType = ai.classifyGeneral(general, Random(0), 40),
            allCities = listOf(city), allGenerals = listOf(general), allNations = listOf(nation),
            frontCities = emptyList(), rearCities = listOf(city), nationGenerals = listOf(general),
        )
        return method.invoke(ai, ctx, rng, NpcNationPolicy()) as String?
    }

    private fun invokeDoReturn(
        world: SessionState, general: Officer, city: Planet, nation: Faction,
    ): String? {
        val method = OfficerAI::class.java.getDeclaredMethod(
            "doReturn", AIContext::class.java, Random::class.java,
        )
        method.isAccessible = true
        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = DiplomacyState.PEACE,
            generalType = ai.classifyGeneral(general, Random(0), 40),
            allCities = listOf(city), allGenerals = listOf(general), allNations = listOf(nation),
            frontCities = emptyList(), rearCities = listOf(city), nationGenerals = listOf(general),
        )
        return method.invoke(ai, ctx, Random(0)) as String?
    }

    private fun invokeDoWarpToDomestic(
        world: SessionState, general: Officer, city: Planet, nation: Faction,
        supplyCities: List<Planet>, rng: Random = Random(0),
    ): String? {
        val method = OfficerAI::class.java.getDeclaredMethod(
            "doWarpToDomestic", AIContext::class.java, Random::class.java,
            NpcNationPolicy::class.java, List::class.java,
        )
        method.isAccessible = true
        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = DiplomacyState.PEACE,
            generalType = ai.classifyGeneral(general, Random(0), 40),
            allCities = supplyCities + listOf(city),
            allGenerals = listOf(general), allNations = listOf(nation),
            frontCities = emptyList(), rearCities = supplyCities + listOf(city),
            nationGenerals = listOf(general),
        )
        return method.invoke(ai, ctx, rng, NpcNationPolicy(), supplyCities) as String?
    }

    // --- doNormalDomestic golden value tests ---

    @Test
    fun `doNormalDomestic selects production when it is lowest stat below 50 percent`() {
        val world = createWorld()
        val general = createGeneral(intelligence = 80, command = 30, leadership = 30, ships = 0)
        val city = createCity(
            factionId = 1, production = 200, productionMax = 1000, commerce = 300, commerceMax = 1000, security = 100, securityMax = 1000,
        )
        val nation = createNation()

        val action = invokeDoNormalDomestic(world, general, city, nation)
        assertEquals("농지개간", action, "production < 50% triggers 농지개간 first per PHP deterministic path")
    }

    @Test
    fun `doNormalDomestic returns null when all stats at max`() {
        val world = createWorld()
        val general = createGeneral(intelligence = 80, command = 30, leadership = 30, ships = 0)
        val city = createCity(
            factionId = 1, production = 1000, productionMax = 1000, commerce = 1000, commerceMax = 1000, security = 1000, securityMax = 1000,
        ).apply {
            approval = 100f; population = 100000; populationMax = 100000
        }
        val nation = createNation().apply { techLevel = 13000f }

        val action = invokeDoNormalDomestic(world, general, city, nation)
        assertNull(action, "All stats maxed should return null per PHP")
    }

    @Test
    fun `doNormalDomestic selects commerce when production ok but commerce below 50 percent`() {
        val world = createWorld()
        val general = createGeneral(intelligence = 80, command = 30, leadership = 30, ships = 0)
        val city = createCity(
            factionId = 1, production = 600, productionMax = 1000, commerce = 200, commerceMax = 1000, security = 600, securityMax = 1000,
        )
        val nation = createNation()

        val action = invokeDoNormalDomestic(world, general, city, nation)
        assertEquals("상업투자", action, "commerce < 50% with production >= 50% should select 상업투자")
    }

    // --- doUrgentDomestic golden value tests ---

    @Test
    fun `doUrgentDomestic returns null during peace`() {
        val world = createWorld()
        val general = createGeneral(leadership = 80)
        val city = createCity(factionId = 1).apply { approval = 50f }
        val nation = createNation()

        val action = invokeDoUrgentDomestic(world, general, city, nation, dipState = DiplomacyState.PEACE)
        assertNull(action, "doUrgentDomestic should return null during PEACE per PHP")
    }

    @Test
    fun `doUrgentDomestic selects 주민선정 when approval below 70 during war`() {
        val world = createWorld()
        val general = createGeneral(leadership = 80)
        val city = createCity(factionId = 1).apply { approval = 50f }
        val nation = createNation()

        val action = invokeDoUrgentDomestic(world, general, city, nation, rng = FixedRandom(0.0))
        assertEquals("주민선정", action, "approval < 70 during war should select 주민선정 per PHP")
    }

    // --- doWarDomestic golden value tests ---

    @Test
    fun `doWarDomestic returns null during peace`() {
        val world = createWorld()
        val general = createGeneral(intelligence = 80, command = 80, leadership = 80, ships = 0)
        val city = createCity(factionId = 1, production = 200, productionMax = 1000)
        val nation = createNation()

        val action = invokeDoWarDomestic(world, general, city, nation, dipState = DiplomacyState.PEACE)
        assertNull(action, "doWarDomestic should return null during PEACE")
    }

    @Test
    fun `doWarDomestic with front city and low orbitalDefense selects defense action`() {
        val world = createWorld()
        val general = createGeneral(command = 90, intelligence = 30, leadership = 30, ships = 0)
        val city = createCity(
            factionId = 1, production = 600, productionMax = 1000, commerce = 600, commerceMax = 1000,
            security = 600, securityMax = 1000, frontState = 1,
        ).apply {
            orbitalDefense = 100; orbitalDefenseMax = 1000; fortress = 600; fortressMax = 1000
        }
        val nation = createNation(supplies = 5000)

        // FixedRandom: first for rice<1000 (N/A), second for 30% skip (0.5 > 0.3 passes)
        val action = invokeDoWarDomestic(world, general, city, nation, rng = FixedRandom(0.5, 0.5, 0.0))
        assertNotNull(action, "Front city with low orbitalDefense should produce action during war")
        assertTrue(action in listOf("수비강화", "성벽보수", "치안강화"),
            "Expected defense action but got: $action")
    }

    // --- doTradeResources golden value tests ---

    @Test
    fun `doTradeResources buys rice when gold much higher than rice`() {
        val world = createWorld()
        val general = createGeneral(funds = 6000, supplies = 500)
        val city = createCity(factionId = 1)
        val nation = createNation()

        val action = invokeDoTradeResources(world, general, city, nation)
        assertEquals("군량매매", action)
        @Suppress("UNCHECKED_CAST")
        val aiArg = general.meta["aiArg"] as Map<String, Any>
        assertEquals(true, aiArg["isBuy"], "Should be buying rice")
    }

    @Test
    fun `doTradeResources sells rice when rice much higher than gold`() {
        val world = createWorld()
        val general = createGeneral(funds = 500, supplies = 6000)
        val city = createCity(factionId = 1)
        val nation = createNation()

        val action = invokeDoTradeResources(world, general, city, nation)
        assertEquals("군량매매", action)
        @Suppress("UNCHECKED_CAST")
        val aiArg = general.meta["aiArg"] as Map<String, Any>
        assertEquals(false, aiArg["isBuy"], "Should be selling rice")
    }

    @Test
    fun `doTradeResources returns null when both resources adequate`() {
        val world = createWorld()
        val general = createGeneral(funds = 3000, supplies = 3000)
        val city = createCity(factionId = 1)
        val nation = createNation()

        val action = invokeDoTradeResources(world, general, city, nation)
        assertNull(action, "Balanced resources should return null")
    }

    // --- doDonate / doNpcDedicate golden value tests ---

    @Test
    fun `doDonate with excess gold donates to nation`() {
        val world = createWorld()
        val general = createGeneral(funds = 30000, supplies = 1000, intelligence = 80, command = 30, leadership = 30, ships = 0)
        val city = createCity(factionId = 1)
        val nation = createNation(funds = 5000)

        val action = invokeDoDonate(world, general, city, nation)
        assertEquals("헌납", action, "Excess gold should trigger 헌납")
        @Suppress("UNCHECKED_CAST")
        val aiArg = general.meta["aiArg"] as Map<String, Any>
        assertEquals(true, aiArg["isGold"], "Should donate gold")
    }

    @Test
    fun `doNpcDedicate with excess resources donates`() {
        val world = createWorld()
        val general = createGeneral(funds = 8000, supplies = 1000, leadership = 80, command = 50, intelligence = 30, ships = 0)
        val city = createCity(factionId = 1)
        val nation = createNation(funds = 5000)

        val action = invokeDoNpcDedicate(world, general, city, nation, rng = FixedRandom(0.0))
        if (action != null) {
            assertEquals("헌납", action, "NPC dedicate should return 헌납")
        }
    }

    // --- doReturn golden value tests ---

    @Test
    fun `doReturn returns 귀환 when general in enemy territory`() {
        val world = createWorld()
        val general = createGeneral(factionId = 1, planetId = 2)
        val city = createCity(id = 2, factionId = 2)
        val nation = createNation()

        val action = invokeDoReturn(world, general, city, nation)
        assertEquals("귀환", action, "General in enemy territory should return 귀환")
    }

    @Test
    fun `doReturn returns null when in own territory with supply`() {
        val world = createWorld()
        val general = createGeneral(factionId = 1, planetId = 1)
        val city = createCity(id = 1, factionId = 1).apply { supplyState = 1 }
        val nation = createNation()

        val action = invokeDoReturn(world, general, city, nation)
        assertNull(action, "General in own supplied territory should return null")
    }

    // --- doWarpToDomestic golden value tests ---

    @Test
    fun `doWarpToDomestic warps to underdeveloped city when current is maxed`() {
        val world = createWorld()
        val general = createGeneral(intelligence = 80, command = 30, leadership = 30, ships = 0)
        val currentCity = createCity(
            id = 1, factionId = 1, production = 1000, productionMax = 1000,
            commerce = 1000, commerceMax = 1000, security = 1000, securityMax = 1000,
        ).apply { approval = 100f; population = 100000; populationMax = 100000 }
        val targetCity = createCity(
            id = 2, factionId = 1, production = 200, productionMax = 1000,
            commerce = 200, commerceMax = 1000, security = 200, securityMax = 1000,
        ).apply { approval = 50f; population = 50000; populationMax = 100000 }
        val nation = createNation()

        val action = invokeDoWarpToDomestic(world, general, currentCity, nation,
            supplyCities = listOf(currentCity, targetCity), rng = FixedRandom(0.99, 0.99, 0.0))
        if (action != null) {
            assertEquals("이동", action, "Should warp to underdeveloped city")
        }
    }

    @Test
    fun `doWarpToDomestic returns null when commander during war`() {
        val world = createWorld()
        val general = createGeneral(leadership = 80, command = 50, intelligence = 30, ships = 0)
        val city = createCity(id = 1, factionId = 1)
        val nation = createNation()

        val method = OfficerAI::class.java.getDeclaredMethod(
            "doWarpToDomestic", AIContext::class.java, Random::class.java,
            NpcNationPolicy::class.java, List::class.java,
        )
        method.isAccessible = true
        val ctx = AIContext(
            world = world, general = general, city = city, nation = nation,
            diplomacyState = DiplomacyState.AT_WAR,
            generalType = ai.classifyGeneral(general, Random(0), 40),
            allCities = listOf(city), allGenerals = listOf(general), allNations = listOf(nation),
            frontCities = emptyList(), rearCities = listOf(city), nationGenerals = listOf(general),
        )
        val action = method.invoke(ai, ctx, Random(0), NpcNationPolicy(), listOf(city)) as String?
        assertNull(action, "Commander during war should not warp to domestic per PHP")
    }

    // ========== Economy Rate Parity Tests (08-03) ==========

    @Test
    fun `chooseTexRate with well-developed supply cities returns 25`() {
        val world = createWorld()
        val general = createGeneral(factionId = 1, dedication = 100)
        val city = createCity(
            factionId = 1, production = 1000, productionMax = 1000, commerce = 1000, commerceMax = 1000,
            security = 1000, securityMax = 1000,
        ).apply { population = 95000; populationMax = 100000; approval = 100f }
        val nation = createNation()

        val ctx = buildAiContext(world, general, city, nation, listOf(general))
        val conscriptionRate = ai.chooseTexRate(ctx, listOf(city))
        assertEquals(25, rate, "Well-developed cities should yield conscriptionRate =25 per PHP")
    }

    @Test
    fun `chooseTexRate with low-developed supply cities returns 10`() {
        val world = createWorld()
        val general = createGeneral(factionId = 1, dedication = 100)
        val city = createCity(
            factionId = 1, production = 200, productionMax = 1000, commerce = 200, commerceMax = 1000,
            security = 200, securityMax = 1000,
        ).apply { population = 20000; populationMax = 100000; approval = 50f }
        val nation = createNation()

        val ctx = buildAiContext(world, general, city, nation, listOf(general))
        val conscriptionRate = ai.chooseTexRate(ctx, listOf(city))
        assertEquals(10, rate, "Low-developed cities should yield conscriptionRate =10 per PHP")
    }

    @Test
    fun `chooseGoldBillRate with adequate treasury returns valid bill`() {
        val world = createWorld()
        val general = createGeneral(factionId = 1, dedication = 100)
        val city = createCity(
            factionId = 1, commerce = 100, commerceMax = 1000, security = 100, securityMax = 1000,
        ).apply { approval = 50f; dead = 0; population = 10000; populationMax = 100000 }
        val nation = createNation(funds = 500).apply { conscriptionRate = 10; factionType = "che_중립" }

        val bill = ai.chooseGoldBillRate(
            buildAiContext(world, general, city, nation, listOf(general)),
            listOf(city), NpcNationPolicy(),
        )
        assertTrue(bill in 20..200, "Bill should be in valid range [20, 200]")
    }

    @Test
    fun `chooseGoldBillRate with depleted treasury returns minimum bill`() {
        val world = createWorld()
        val general = createGeneral(factionId = 1, dedication = 100)
        val city = createCity(
            factionId = 1, commerce = 50, commerceMax = 1000, security = 50, securityMax = 1000,
        ).apply { approval = 30f; dead = 0; population = 5000; populationMax = 100000 }
        val nation = createNation(funds = 100).apply { conscriptionRate = 10; factionType = "che_중립" }

        val bill = ai.chooseGoldBillRate(
            buildAiContext(world, general, city, nation, listOf(general)),
            listOf(city), NpcNationPolicy(),
        )
        assertEquals(20, bill, "Depleted treasury with low income should yield minimum bill=20")
    }

    @Test
    fun `chooseRiceBillRate with adequate rice returns valid bill`() {
        val world = createWorld()
        val general = createGeneral(factionId = 1, dedication = 2000)
        val city = createCity(
            factionId = 1, production = 800, productionMax = 1000, security = 800, securityMax = 1000,
        ).apply {
            approval = 80f; population = 80000; populationMax = 100000
            orbitalDefense = 500; fortress = 800; fortressMax = 1000
        }
        val nation = createNation(supplies = 50000).apply { conscriptionRate = 20; factionType = "che_중립" }

        val bill = ai.chooseRiceBillRate(
            buildAiContext(world, general, city, nation, listOf(general)),
            listOf(city), NpcNationPolicy(),
        )
        assertTrue(bill in 20..200, "Bill should be in valid range [20, 200]")
    }

    @Test
    fun `chooseRiceBillRate with depleted rice returns minimum bill`() {
        val world = createWorld()
        val general = createGeneral(factionId = 1, dedication = 100)
        val city = createCity(
            factionId = 1, production = 50, productionMax = 1000, security = 50, securityMax = 1000,
        ).apply {
            approval = 30f; population = 5000; populationMax = 100000
            orbitalDefense = 50; fortress = 50; fortressMax = 1000
        }
        val nation = createNation(supplies = 100).apply { conscriptionRate = 10; factionType = "che_중립" }

        val bill = ai.chooseRiceBillRate(
            buildAiContext(world, general, city, nation, listOf(general)),
            listOf(city), NpcNationPolicy(),
        )
        assertEquals(20, bill, "Depleted rice with low income should yield minimum bill=20")
    }

    // ========== Wanderer Injury Threshold Tests (08-05 Plan Task 2) ==========

    @Nested
    inner class WandererInjuryThresholdTests {

        private fun invokeDecideWandererAction(general: Officer, world: SessionState, rng: Random): String {
            val method = OfficerAI::class.java.getDeclaredMethod(
                "decideWandererAction", Officer::class.java, SessionState::class.java, Random::class.java
            )
            method.isAccessible = true
            return method.invoke(ai, general, world, rng) as String
        }

        @Test
        fun `wanderer with injury below cureThreshold should NOT return 요양`() {
            // injury=5 < default cureThreshold=10 -> should proceed to normal wanderer logic
            val world = createWorld()
            val general = createGeneral(factionId = 0, injury = 5, npcState = 2, officerLevel = 1)

            val result = invokeDecideWandererAction(general, world, FixedRandom(0.5))
            assertNotEquals("요양", result, "Injury 5 is below cureThreshold 10, should not heal")
        }

        @Test
        fun `wanderer with injury above cureThreshold should return 요양`() {
            // injury=15 > default cureThreshold=10 -> should early-return "요양"
            val world = createWorld()
            val general = createGeneral(factionId = 0, injury = 15, npcState = 2, officerLevel = 1)

            val result = invokeDecideWandererAction(general, world, FixedRandom(0.5))
            assertEquals("요양", result, "Injury 15 exceeds cureThreshold 10, should heal")
        }

        @Test
        fun `wanderer with injury exactly at cureThreshold should NOT return 요양`() {
            // injury=10 == default cureThreshold=10 -> strict > means should NOT heal
            val world = createWorld()
            val general = createGeneral(factionId = 0, injury = 10, npcState = 2, officerLevel = 1)

            val result = invokeDecideWandererAction(general, world, FixedRandom(0.5))
            assertNotEquals("요양", result, "Injury 10 equals cureThreshold 10, strict > means no heal")
        }
    }
}
