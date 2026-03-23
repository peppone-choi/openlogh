package com.openlogh.engine.ai

import com.openlogh.entity.*
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

class FactionAITest {

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
        ai = FactionAI(
            officerRepository = officerRepository,
            planetRepository = planetRepository,
            factionRepository = factionRepository,
            diplomacyRepository = diplomacyRepository,
        )
    }

    private fun createWorld(year: Short = 200, month: Short = 3): SessionState {
        return SessionState(id = 1, scenarioCode = "test", currentYear = year, currentMonth = month, tickSeconds = 300)
    }

    private fun createFaction(
        id: Long = 1,
        funds: Int = 10000,
        supplies: Int = 10000,
        militaryPower: Int = 100,
        warState: Short = 0,
        strategicCmdLimit: Short = 0,
    ): Faction {
        return Faction(
            id = id,
            sessionId = 1,
            name = "국가$id",
            color = "#FF0000",
            funds = funds,
            supplies = supplies,
            militaryPower = militaryPower,
            warState = warState,
            strategicCmdLimit = strategicCmdLimit,
            capitalPlanetId = 1,
        )
    }

    private fun createOfficer(
        id: Long = 1,
        factionId: Long = 1,
        planetId: Long = 1,
        npcState: Short = 2,
        rank: Short = 1,
        dedication: Int = 100,
    ): Officer {
        return Officer(
            id = id,
            sessionId = 1,
            name = "장수$id",
            factionId = factionId,
            planetId = planetId,
            npcState = npcState,
            rank = rank,
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

    private fun createPlanet(
        id: Long = 1,
        factionId: Long = 1,
        level: Short = 5,
    ): Planet {
        return Planet(
            id = id,
            sessionId = 1,
            name = "도시$id",
            factionId = factionId,
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
        faction: Faction,
        planets: List<Planet>,
        officers: List<Officer>,
        allFactions: List<Faction> = listOf(faction),
        diplomacies: List<Diplomacy> = emptyList(),
    ) {
        `when`(planetRepository.findByFactionId(faction.id)).thenReturn(planets)
        `when`(officerRepository.findBySessionIdAndNationId(1L, faction.id)).thenReturn(officers)
        `when`(factionRepository.findBySessionId(1L)).thenReturn(allFactions)
        `when`(diplomacyRepository.findBySessionIdAndIsDeadFalse(1L)).thenReturn(diplomacies)
    }

    @Test
    fun `should declare war when stronger and sufficiently prepared`() {
        val faction = createFaction(funds = 20000, supplies = 20000, militaryPower = 200)
        val target = createFaction(id = 2, militaryPower = 50)
        `when`(planetRepository.findByFactionId(1)).thenReturn(listOf(createPlanet(id = 1), createPlanet(id = 2)))
        `when`(planetRepository.findByFactionId(2)).thenReturn(listOf(createPlanet(id = 3, factionId = 2)))
        `when`(officerRepository.findBySessionIdAndNationId(1L, 1L)).thenReturn(listOf(createOfficer(id = 1), createOfficer(id = 2)))
        `when`(officerRepository.findBySessionIdAndNationId(1L, 2L)).thenReturn(listOf(createOfficer(id = 3, factionId = 2)))

        assertTrue(ai.shouldDeclareWar(faction, target, createWorld()))
    }

    @Test
    fun `should not declare war when weaker than target`() {
        val faction = createFaction(funds = 20000, supplies = 20000, militaryPower = 50)
        val target = createFaction(id = 2, militaryPower = 200)
        `when`(planetRepository.findByFactionId(1)).thenReturn(listOf(createPlanet()))
        `when`(planetRepository.findByFactionId(2)).thenReturn(listOf(createPlanet(id = 2, factionId = 2)))
        `when`(officerRepository.findBySessionIdAndNationId(1L, 1L)).thenReturn(listOf(createOfficer()))
        `when`(officerRepository.findBySessionIdAndNationId(1L, 2L)).thenReturn(listOf(createOfficer(id = 2, factionId = 2)))

        assertFalse(ai.shouldDeclareWar(faction, target, createWorld()))
    }

    @Test
    fun `decideNationAction returns Nation휴식 when nation gold is very low`() {
        val faction = createFaction(funds = 500)
        setupRepos(faction, listOf(createPlanet()), listOf(createOfficer()))

        val action = ai.decideNationAction(faction, createWorld(), Random(42))
        assertEquals("Nation휴식", action)
    }

    @Test
    fun `decideNationAction returns war strategic action when at war and strategic commands available`() {
        val faction = createFaction(funds = 20000, supplies = 20000, warState = 1, strategicCmdLimit = 2)
        setupRepos(faction, listOf(createPlanet()), listOf(createOfficer()))

        val action = ai.decideNationAction(faction, createWorld(), Random(42))
        assertTrue(action in listOf("급습", "의병모집", "필사즉생"))
    }

    @Test
    fun `decideNationAction returns Nation휴식 when at war but no strategic command available`() {
        val faction = createFaction(funds = 20000, supplies = 20000, warState = 1, strategicCmdLimit = 0)
        setupRepos(faction, listOf(createPlanet()), listOf(createOfficer()))

        val action = ai.decideNationAction(faction, createWorld(), Random(42))
        assertEquals("Nation휴식", action)
    }

    @Test
    fun `decideNationAction returns 발령 when unassigned generals exist`() {
        val faction = createFaction(funds = 20000, supplies = 20000)
        val unassigned = createOfficer(id = 1, rank = 0, npcState = 2)
        setupRepos(faction, listOf(createPlanet()), listOf(unassigned))

        val action = ai.decideNationAction(faction, createWorld(), Random(42))
        assertEquals("발령", action)
    }

    @Test
    fun `decideNationAction returns 증축 when nation can expand low-level city`() {
        val faction = createFaction(funds = 20000, supplies = 20000)
        val planets = listOf(createPlanet(id = 1, level = 4), createPlanet(id = 2, level = 5))
        val officers = listOf(createOfficer(id = 1, rank = 1, dedication = 120))
        setupRepos(faction, planets, officers)

        val action = ai.decideNationAction(faction, createWorld(), Random(42))
        assertEquals("증축", action)
    }

    @Test
    fun `decideNationAction returns 포상 when generals have low dedication and nation has enough gold`() {
        val faction = createFaction(funds = 15000, supplies = 15000)
        val planets = listOf(createPlanet(level = 5))
        val lowDedOfficer = createOfficer(id = 1, rank = 1, dedication = 50)
        setupRepos(faction, planets, listOf(lowDedOfficer))

        val action = ai.decideNationAction(faction, createWorld(), Random(42))
        assertEquals("포상", action)
    }

    @Test
    fun `decideNationAction falls back to Nation휴식 when no trigger condition matches`() {
        val faction = createFaction(funds = 20000, supplies = 20000, militaryPower = 100)
        val planets = listOf(createPlanet(level = 5))
        val officers = listOf(createOfficer(id = 1, rank = 3, dedication = 120))
        setupRepos(
            faction = faction,
            planets = planets,
            officers = officers,
            allFactions = listOf(faction),
            diplomacies = emptyList(),
        )

        val action = ai.decideNationAction(faction, createWorld(), Random(42))
        assertEquals("Nation휴식", action)
    }

    // ========== Policy threshold integration ==========

    @Test
    fun `decideNationAction returns Nation휴식 when gold below reqNationGold threshold`() {
        // Default reqNationGold = 10000, so gold = 1500 should trigger rest
        val faction = createFaction(funds = 1500, supplies = 10000)
        setupRepos(faction, listOf(createPlanet()), listOf(createOfficer()))

        val action = ai.decideNationAction(faction, createWorld(), Random(42))
        assertEquals("Nation휴식", action)
    }

    @Test
    fun `decideNationAction returns Nation휴식 when rice below reqNationRice threshold`() {
        // Default reqNationRice = 12000, so rice = 1500 should trigger rest
        val faction = createFaction(funds = 10000, supplies = 1500)
        setupRepos(faction, listOf(createPlanet()), listOf(createOfficer()))

        val action = ai.decideNationAction(faction, createWorld(), Random(42))
        assertEquals("Nation휴식", action)
    }

    // ========== Capital relocation ==========

    @Test
    fun `decideNationAction does not consider 천도 with only one city`() {
        val faction = createFaction(funds = 10000, supplies = 10000)
        faction.capitalPlanetId = 1
        val planet = createPlanet(id = 1, level = 5)
        val officers = listOf(createOfficer(id = 1, rank = 3, dedication = 120))
        setupRepos(faction, listOf(planet), officers)

        // With a single city, 천도 should not appear as candidate
        val action = ai.decideNationAction(faction, createWorld(), Random(42))
        assertNotEquals("천도", action)
    }
}
