package com.openlogh.engine.ai

import com.openlogh.entity.Planet
import com.openlogh.entity.Diplomacy
import com.openlogh.entity.Officer
import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.DiplomacyRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.FactionRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import kotlin.random.Random

class NationAITest {

    private lateinit var ai: FactionAI
    private lateinit var planetRepository: PlanetRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var diplomacyRepository: DiplomacyRepository

    @BeforeEach
    fun setUp() {
        planetRepository = mock(PlanetRepository::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        diplomacyRepository = mock(DiplomacyRepository::class.java)
        ai = FactionAI(JpaWorldPortFactory(
            officerRepository = officerRepository,
            planetRepository = planetRepository,
            factionRepository = factionRepository,
            diplomacyRepository = diplomacyRepository,
        ))
    }

    private fun createWorld(year: Short = 200, month: Short = 3): SessionState {
        return SessionState(id = 1, scenarioCode = "test", currentYear = year, currentMonth = month, tickSeconds = 300)
    }

    private fun createNation(
        id: Long = 1,
        gold: Int = 10000,
        rice: Int = 10000,
        power: Int = 100,
        warState: Short = 0,
        strategicCmdLimit: Short = 0,
    ): Faction {
        return Faction(
            id = id,
            sessionId = 1,
            name = "국가$id",
            color = "#FF0000",
            funds = gold,
            supplies = rice,
            militaryPower = power,
            warState = warState,
            strategicCmdLimit = strategicCmdLimit,
            capitalPlanetId = 1,
        )
    }

    private fun createGeneral(
        id: Long = 1,
        nationId: Long = 1,
        cityId: Long = 1,
        npcState: Short = 2,
        officerLevel: Short = 1,
        dedication: Int = 100,
    ): Officer {
        return Officer(
            id = id,
            sessionId = 1,
            name = "장수$id",
            factionId = nationId,
            planetId = cityId,
            npcState = npcState,
            officerLevel = officerLevel,
            dedication = dedication,
            leadership = 50,
            command = 50,
            intelligence = 50,
            politics = 50,
            administration = 50,
            funds = 1000,
            supplies = 1000,
            turnTime = OffsetDateTime.now(),
        )
    }

    private fun createCity(
        id: Long = 1,
        nationId: Long = 1,
        level: Short = 5,
    ): Planet {
        return Planet(
            id = id,
            sessionId = 1,
            name = "도시$id",
            factionId = nationId,
            level = level,
            population = 10000,
            populationMax = 50000,
            production = 500,
            productionMax = 1000,
            commerce = 500,
            commerceMax = 1000,
            security = 500,
            securityMax = 1000,
        )
    }

    private fun setupRepos(
        nation: Faction,
        cities: List<Planet>,
        generals: List<Officer>,
        allNations: List<Faction> = listOf(nation),
        diplomacies: List<Diplomacy> = emptyList(),
    ) {
        `when`(planetRepository.findByFactionId(nation.id)).thenReturn(cities)
        `when`(officerRepository.findBySessionIdAndFactionId(1L, nation.id)).thenReturn(generals)
        `when`(factionRepository.findBySessionId(1L)).thenReturn(allNations)
        `when`(diplomacyRepository.findByWorldIdAndIsDeadFalse(1L)).thenReturn(diplomacies)
    }

    @Test
    fun `should declare war when stronger and sufficiently prepared`() {
        val nation = createNation(gold = 20000, rice = 20000, power = 200)
        val target = createNation(id = 2, power = 50)
        `when`(planetRepository.findByFactionId(1)).thenReturn(listOf(createCity(id = 1), createCity(id = 2)))
        `when`(planetRepository.findByFactionId(2)).thenReturn(listOf(createCity(id = 3, nationId = 2)))
        `when`(officerRepository.findBySessionIdAndFactionId(1L, 1L)).thenReturn(listOf(createGeneral(id = 1), createGeneral(id = 2)))
        `when`(officerRepository.findBySessionIdAndFactionId(1L, 2L)).thenReturn(listOf(createGeneral(id = 3, nationId = 2)))

        assertTrue(ai.shouldDeclareWar(nation, target, createWorld()))
    }

    @Test
    fun `should not declare war when weaker than target`() {
        val nation = createNation(gold = 20000, rice = 20000, power = 50)
        val target = createNation(id = 2, power = 200)
        `when`(planetRepository.findByFactionId(1)).thenReturn(listOf(createCity()))
        `when`(planetRepository.findByFactionId(2)).thenReturn(listOf(createCity(id = 2, nationId = 2)))
        `when`(officerRepository.findBySessionIdAndFactionId(1L, 1L)).thenReturn(listOf(createGeneral()))
        `when`(officerRepository.findBySessionIdAndFactionId(1L, 2L)).thenReturn(listOf(createGeneral(id = 2, nationId = 2)))

        assertFalse(ai.shouldDeclareWar(nation, target, createWorld()))
    }

    @Test
    fun `decideNationAction returns Nation휴식 when nation gold is very low`() {
        val nation = createNation(gold = 500)
        setupRepos(nation, listOf(createCity()), listOf(createGeneral()))

        val action = ai.decideNationAction(nation, createWorld(), Random(42))
        assertEquals("Nation휴식", action)
    }

    @Test
    fun `decideNationAction returns war strategic action when at war and strategic commands available`() {
        val nation = createNation(gold = 20000, rice = 20000, warState = 1, strategicCmdLimit = 2)
        setupRepos(nation, listOf(createCity()), listOf(createGeneral()))

        val action = ai.decideNationAction(nation, createWorld(), Random(42))
        assertTrue(action in listOf("급습", "의병모집", "필사즉생"))
    }

    @Test
    fun `decideNationAction returns Nation휴식 when at war but no strategic command available`() {
        val nation = createNation(gold = 20000, rice = 20000, warState = 1, strategicCmdLimit = 0)
        setupRepos(nation, listOf(createCity()), listOf(createGeneral()))

        val action = ai.decideNationAction(nation, createWorld(), Random(42))
        assertEquals("Nation휴식", action)
    }

    @Test
    fun `decideNationAction returns 발령 when unassigned generals exist`() {
        val nation = createNation(gold = 20000, rice = 20000)
        val unassigned = createGeneral(id = 1, officerLevel = 0, npcState = 2)
        setupRepos(nation, listOf(createCity()), listOf(unassigned))

        val action = ai.decideNationAction(nation, createWorld(), Random(42))
        assertEquals("발령", action)
    }

    @Test
    fun `decideNationAction returns 증축 when nation can expand low-level city`() {
        val nation = createNation(gold = 20000, rice = 20000)
        val cities = listOf(createCity(id = 1, level = 4), createCity(id = 2, level = 5))
        val generals = listOf(createGeneral(id = 1, officerLevel = 1, dedication = 120))
        setupRepos(nation, cities, generals)

        val action = ai.decideNationAction(nation, createWorld(), Random(42))
        assertEquals("증축", action)
    }

    @Test
    fun `decideNationAction returns 포상 when generals have low dedication and nation has enough gold`() {
        val nation = createNation(gold = 15000, rice = 15000)
        val cities = listOf(createCity(level = 5))
        val lowDedGeneral = createGeneral(id = 1, officerLevel = 1, dedication = 50)
        setupRepos(nation, cities, listOf(lowDedGeneral))

        val action = ai.decideNationAction(nation, createWorld(), Random(42))
        assertEquals("포상", action)
    }

    @Test
    fun `decideNationAction falls back to Nation휴식 when no trigger condition matches`() {
        val nation = createNation(gold = 20000, rice = 20000, power = 100)
        val cities = listOf(createCity(level = 5))
        val generals = listOf(createGeneral(id = 1, officerLevel = 3, dedication = 120))
        setupRepos(
            nation = nation,
            cities = cities,
            generals = generals,
            allNations = listOf(nation),
            diplomacies = emptyList(),
        )

        val action = ai.decideNationAction(nation, createWorld(), Random(42))
        assertEquals("Nation휴식", action)
    }

    // ========== Policy threshold integration ==========

    @Test
    fun `decideNationAction returns Nation휴식 when gold below reqNationGold threshold`() {
        // Default reqNationGold = 10000, so gold = 1500 should trigger rest
        val nation = createNation(gold = 1500, rice = 10000)
        setupRepos(nation, listOf(createCity()), listOf(createGeneral()))

        val action = ai.decideNationAction(nation, createWorld(), Random(42))
        assertEquals("Nation휴식", action)
    }

    @Test
    fun `decideNationAction returns Nation휴식 when rice below reqNationRice threshold`() {
        // Default reqNationRice = 12000, so rice = 1500 should trigger rest
        val nation = createNation(gold = 10000, rice = 1500)
        setupRepos(nation, listOf(createCity()), listOf(createGeneral()))

        val action = ai.decideNationAction(nation, createWorld(), Random(42))
        assertEquals("Nation휴식", action)
    }

    // ========== Capital relocation ==========

    @Test
    fun `decideNationAction does not consider 천도 with only one city`() {
        val nation = createNation(gold = 10000, rice = 10000)
        nation.capitalPlanetId = 1
        val city = createCity(id = 1, level = 5)
        val generals = listOf(createGeneral(id = 1, officerLevel = 3, dedication = 120))
        setupRepos(nation, listOf(city), generals)

        // With a single city, 천도 should not appear as candidate
        val action = ai.decideNationAction(nation, createWorld(), Random(42))
        assertNotEquals("천도", action)
    }
}
