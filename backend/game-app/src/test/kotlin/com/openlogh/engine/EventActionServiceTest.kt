package com.openlogh.engine

import com.openlogh.entity.*
import com.openlogh.repository.*
import com.openlogh.service.HistoryService
import com.openlogh.service.InheritanceService
import com.openlogh.service.ScenarioService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*

/**
 * Unit tests for EventActionService — verifying Kotlin implementations match PHP legacy behavior.
 *
 * PHP references: /ref/core/hwe/sammo/Event/Action/
 */
class EventActionServiceTest {

    private lateinit var service: EventActionService
    private lateinit var officerRepository: OfficerRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var eventRepository: EventRepository
    private lateinit var officerTurnRepository: OfficerTurnRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var bettingRepository: BettingRepository
    private lateinit var betEntryRepository: BetEntryRepository
    private lateinit var historyService: HistoryService
    private lateinit var scenarioService: ScenarioService
    private lateinit var specialAssignmentService: SpecialAssignmentService
    private lateinit var rankDataRepository: RankDataRepository
    private lateinit var inheritanceService: InheritanceService

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNonNull(): T = any<T>() as T

    @BeforeEach
    fun setUp() {
        officerRepository = mock(OfficerRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        eventRepository = mock(EventRepository::class.java)
        officerTurnRepository = mock(OfficerTurnRepository::class.java)
        messageRepository = mock(MessageRepository::class.java)
        bettingRepository = mock(BettingRepository::class.java)
        betEntryRepository = mock(BetEntryRepository::class.java)
        historyService = mock(HistoryService::class.java)
        scenarioService = mock(ScenarioService::class.java)
        specialAssignmentService = mock(SpecialAssignmentService::class.java)
        rankDataRepository = mock(RankDataRepository::class.java)
        inheritanceService = mock(InheritanceService::class.java)

        service = EventActionService(
            officerRepository, factionRepository, planetRepository,
            eventRepository, officerTurnRepository, messageRepository,
            bettingRepository, betEntryRepository,
            historyService, scenarioService, specialAssignmentService,
            rankDataRepository, inheritanceService,
        )
    }

    private fun createWorld(year: Short = 200, month: Short = 3): SessionState =
        SessionState(id = 1, scenarioCode = "test", currentYear = year, currentMonth = month, tickSeconds = 300)

    private fun createOfficer(
        id: Long = 1L,
        sessionId: Long = 1L,
        factionId: Long = 1L,
        name: String = "장수",
        leadership: Short = 60,
        command: Short = 60,
        intelligence: Short = 60,
        betray: Short = 0,
        age: Short = 25,
        belong: Short = 5,
        rank: Short = 0,
        npcState: Short = 2,
        experience: Int = 1000,
        dedication: Int = 500,
        accessoryCode: String = "None",
    ): Officer = Officer(
        id = id, sessionId = sessionId, factionId = factionId, name = name,
        leadership = leadership, command = command, intelligence = intelligence,
        betray = betray, age = age, belong = belong, rank = rank,
        npcState = npcState, experience = experience, dedication = dedication,
        accessoryCode = accessoryCode,
    )

    private fun createFaction(
        id: Long = 1L,
        sessionId: Long = 1L,
        name: String = "테스트국",
        factionRank: Short = 2,
        scoutLevel: Short = 0,
        funds: Int = 10000,
    ): Faction = Faction(id = id, sessionId = sessionId, name = name, factionRank = factionRank, scoutLevel = scoutLevel, funds = funds)

    private fun createPlanet(
        id: Long = 1L,
        sessionId: Long = 1L,
        factionId: Long = 1L,
        population: Int = 10000,
        populationMax: Int = 50000,
        production: Int = 500, productionMax: Int = 1000,
        commerce: Int = 500, commerceMax: Int = 1000,
        security: Int = 500, securityMax: Int = 1000,
        orbitalDefense: Int = 500, orbitalDefenseMax: Int = 1000,
        fortress: Int = 500, fortressMax: Int = 1000,
        approval: Float = 80f,
        tradeRoute: Int = 100,
        garrisonSet: Int = 0,
        dead: Int = 0,
    ): Planet = Planet(
        id = id, sessionId = sessionId, name = "테스트도시$id", factionId = factionId,
        population = population, populationMax = populationMax, production = production, productionMax = productionMax,
        commerce = commerce, commerceMax = commerceMax, security = security, securityMax = securityMax,
        orbitalDefense = orbitalDefense, orbitalDefenseMax = orbitalDefenseMax, fortress = fortress, fortressMax = fortressMax,
        approval = approval.toInt(), tradeRoute = tradeRoute, garrisonSet = garrisonSet, dead = dead,
    )

    // ─── AddGlobalBetray ───────────────────────────────────────────────────

    @Nested
    inner class AddGlobalBetray {

        @Test
        fun `increases betray for generals at or below ifMax`() {
            // PHP: foreach general where betray <= ifMax → betray += cnt
            val off1 = createOfficer(id = 1, betray = 0)  // betray <= 0 ✓
            val off2 = createOfficer(id = 2, betray = 5)  // betray > 0 ✗
            `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(off1, off2))
            `when`(officerRepository.saveAll(anyNonNull<List<Officer>>())).thenReturn(listOf(off1, off2))

            service.addGlobalBetray(createWorld(), cnt = 1, ifMax = 0)

            assertEquals(1.toShort(), off1.betray)
            assertEquals(5.toShort(), off2.betray)  // unchanged
        }

        @Test
        fun `increases betray by cnt`() {
            val off = createOfficer(betray = 2)
            `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(off))
            `when`(officerRepository.saveAll(anyNonNull<List<Officer>>())).thenReturn(listOf(off))

            service.addGlobalBetray(createWorld(), cnt = 3, ifMax = 10)

            assertEquals(5.toShort(), off.betray)
        }

        @Test
        fun `handles empty general list gracefully`() {
            `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())
            `when`(officerRepository.saveAll(anyNonNull<List<Officer>>())).thenReturn(emptyList())

            assertDoesNotThrow { service.addGlobalBetray(createWorld()) }
        }

        @Test
        fun `does not affect generals above ifMax`() {
            val off = createOfficer(betray = 5)
            `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(off))
            `when`(officerRepository.saveAll(anyNonNull<List<Officer>>())).thenReturn(listOf(off))

            service.addGlobalBetray(createWorld(), cnt = 1, ifMax = 3)

            assertEquals(5.toShort(), off.betray)  // unchanged because betray(5) > ifMax(3)
        }
    }

    // ─── BlockScoutAction / UnblockScoutAction ────────────────────────────

    @Nested
    inner class ScoutActions {

        @Test
        fun `blockScoutAction sets all nation scoutLevel to 1`() {
            // PHP: foreach nation → scout = 1 (blocked)
            val faction1 = createFaction(id = 1, scoutLevel = 0)
            val faction2 = createFaction(id = 2, scoutLevel = 0)
            `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction1, faction2))
            `when`(factionRepository.saveAll(anyNonNull<List<Faction>>())).thenReturn(listOf(faction1, faction2))

            service.blockScoutAction(createWorld())

            assertEquals(1.toShort(), faction1.scoutLevel)
            assertEquals(1.toShort(), faction2.scoutLevel)
        }

        @Test
        fun `unblockScoutAction sets all nation scoutLevel to 0`() {
            // PHP: foreach nation → scout = 0 (unblocked)
            val faction1 = createFaction(id = 1, scoutLevel = 1)
            val faction2 = createFaction(id = 2, scoutLevel = 1)
            `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction1, faction2))
            `when`(factionRepository.saveAll(anyNonNull<List<Faction>>())).thenReturn(listOf(faction1, faction2))

            service.unblockScoutAction(createWorld())

            assertEquals(0.toShort(), faction1.scoutLevel)
            assertEquals(0.toShort(), faction2.scoutLevel)
        }

        @Test
        fun `blockScoutAction propagates blockChangeScout to world config`() {
            val world = createWorld()
            `when`(factionRepository.findBySessionId(1L)).thenReturn(emptyList())
            `when`(factionRepository.saveAll(anyNonNull<List<Faction>>())).thenReturn(emptyList())

            service.blockScoutAction(world, blockChangeScout = true)

            assertEquals(true, world.config["blockChangeScout"])
        }

        @Test
        fun `unblockScoutAction propagates blockChangeScout to world config`() {
            val world = createWorld()
            `when`(factionRepository.findBySessionId(1L)).thenReturn(emptyList())
            `when`(factionRepository.saveAll(anyNonNull<List<Faction>>())).thenReturn(emptyList())

            service.unblockScoutAction(world, blockChangeScout = false)

            assertEquals(false, world.config["blockChangeScout"])
        }
    }

    // ─── ChangePlanet ────────────────────────────────────────────────────────

    @Nested
    inner class ChangePlanet {

        @Test
        fun `applies approval absolute value to all planets when target is null`() {
            // PHP: $actions['trust'] = 50 → planet.approval = 50
            val planet1 = createPlanet(id = 1, approval = 80f)
            val planet2 = createPlanet(id = 2, approval = 60f)
            `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet1, planet2))
            `when`(planetRepository.saveAll(anyNonNull<List<Planet>>())).thenReturn(listOf(planet1, planet2))

            service.changeCity(createWorld(), null, mapOf("trust" to 50))

            assertEquals(50f, planet1.approval, 0.01f)
            assertEquals(50f, planet2.approval, 0.01f)
        }

        @Test
        fun `applies percentage expression to planet tradeRoute`() {
            // PHP: trade = 100 (absolute value)
            val planet = createPlanet(tradeRoute = 95)
            `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))
            `when`(planetRepository.saveAll(anyNonNull<List<Planet>>())).thenReturn(listOf(planet))

            service.changeCity(createWorld(), null, mapOf("trade" to 102))

            assertEquals(102, planet.tradeRoute)
        }

        @Test
        fun `filters planets by free target`() {
            val freePlanet = createPlanet(id = 1, factionId = 0)
            val occupiedPlanet = createPlanet(id = 2, factionId = 1)
            `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(freePlanet, occupiedPlanet))
            `when`(planetRepository.saveAll(anyNonNull<List<Planet>>())).thenReturn(listOf(freePlanet))

            service.changeCity(createWorld(), "free", mapOf("trust" to 100))

            assertEquals(100f, freePlanet.approval, 0.01f)
            // occupied planet should be unchanged
            assertEquals(80f, occupiedPlanet.approval, 0.01f)
        }

        @Test
        fun `applies math expression with plus operator to planet field`() {
            val planet = createPlanet(production = 400, productionMax = 1000)
            `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))
            `when`(planetRepository.saveAll(anyNonNull<List<Planet>>())).thenReturn(listOf(planet))

            // "+100" should add 100 to current production
            service.changeCity(createWorld(), null, mapOf("agri" to "+100"))

            assertEquals(500, planet.production)
        }

        @Test
        fun `clamps approval to 100 maximum`() {
            val planet = createPlanet(approval = 90f)
            `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))
            `when`(planetRepository.saveAll(anyNonNull<List<Planet>>())).thenReturn(listOf(planet))

            service.changeCity(createWorld(), null, mapOf("trust" to 200))

            assertEquals(100f, planet.approval, 0.01f)
        }
    }

    // ─── NewYear ────────────────────────────────────────────────────────────

    @Nested
    inner class NewYear {

        @Test
        fun `increments age for all officers`() {
            // PHP: foreach general → age++
            val off = createOfficer(age = 25, factionId = 1)
            `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(off))
            `when`(officerRepository.saveAll(anyNonNull<List<Officer>>())).thenReturn(listOf(off))

            service.newYear(createWorld())

            assertEquals(26.toShort(), off.age)
        }

        @Test
        fun `increments belong for officers in a faction`() {
            // PHP: foreach general where nation != 0 → belong++
            val off = createOfficer(factionId = 1, belong = 10)
            `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(off))
            `when`(officerRepository.saveAll(anyNonNull<List<Officer>>())).thenReturn(listOf(off))

            service.newYear(createWorld())

            assertEquals(11.toShort(), off.belong)
        }

        @Test
        fun `does not increment belong for wandering officers`() {
            // PHP: only generals in a nation get belong++; nation==0 → wander
            val off = createOfficer(factionId = 0, belong = 5)
            `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(off))
            `when`(officerRepository.saveAll(anyNonNull<List<Officer>>())).thenReturn(listOf(off))

            service.newYear(createWorld())

            assertEquals(5.toShort(), off.belong)  // unchanged
        }

        @Test
        fun `logs history message with current year`() {
            `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())
            `when`(officerRepository.saveAll(anyNonNull<List<Officer>>())).thenReturn(emptyList())

            service.newYear(createWorld(year = 220))

            verify(historyService).logWorldHistory(anyLong(), anyString(), anyInt(), anyInt())
        }
    }

    // ─── ResetOfficerLock ──────────────────────────────────────────────────

    @Nested
    inner class ResetOfficerLock {

        @Test
        fun `resets garrisonSet to 0 for all planets`() {
            // PHP: UPDATE city SET officer_set=0 (all cities)
            val planet = createPlanet(garrisonSet = 1)
            `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))
            `when`(planetRepository.saveAll(anyNonNull<List<Planet>>())).thenReturn(listOf(planet))
            `when`(factionRepository.findBySessionId(1L)).thenReturn(emptyList())
            `when`(factionRepository.saveAll(anyNonNull<List<Faction>>())).thenReturn(emptyList())

            service.resetOfficerLock(createWorld())

            assertEquals(0, planet.garrisonSet)
        }

        @Test
        fun `removes chiefSet from faction meta`() {
            // PHP: unset nation meta chiefSet
            val faction = createFaction()
            faction.meta["chiefSet"] = true
            `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
            `when`(factionRepository.saveAll(anyNonNull<List<Faction>>())).thenReturn(listOf(faction))
            `when`(planetRepository.findBySessionId(1L)).thenReturn(emptyList())
            `when`(planetRepository.saveAll(anyNonNull<List<Planet>>())).thenReturn(emptyList())

            service.resetOfficerLock(createWorld())

            assertFalse(faction.meta.containsKey("chiefSet"))
        }
    }

    // ─── ProcessWarIncome ──────────────────────────────────────────────────

    @Nested
    inner class ProcessWarIncome {

        @Test
        fun `converts dead troops back to population at 20 percent`() {
            // PHP: pop += dead * 0.2; dead = 0
            val planet = createPlanet(factionId = 1, population = 10000, dead = 500)
            `when`(planetRepository.findBySessionId(anyLong())).thenReturn(listOf(planet))
            `when`(planetRepository.saveAll(anyNonNull<List<Planet>>())).thenReturn(listOf(planet))
            `when`(factionRepository.findBySessionId(anyLong())).thenReturn(emptyList())
            `when`(factionRepository.saveAll(anyNonNull<List<Faction>>())).thenReturn(emptyList())

            service.processWarIncome(createWorld())

            // 500 dead * 0.2 = 100 returned as pop
            assertEquals(10100, planet.population)
            assertEquals(0, planet.dead)
        }

        @Test
        fun `zero dead means no pop change`() {
            val planet = createPlanet(population = 10000, dead = 0)
            `when`(planetRepository.findBySessionId(anyLong())).thenReturn(listOf(planet))
            `when`(planetRepository.saveAll(anyNonNull<List<Planet>>())).thenReturn(listOf(planet))
            `when`(factionRepository.findBySessionId(anyLong())).thenReturn(emptyList())
            `when`(factionRepository.saveAll(anyNonNull<List<Faction>>())).thenReturn(emptyList())

            service.processWarIncome(createWorld())

            assertEquals(10000, planet.population)
        }
    }

    // ─── DeleteEvent (via AutoDeleteInvader) ────────────────────────────────

    @Nested
    inner class AutoDeleteInvader {

        @Test
        fun `deletes event when faction does not exist`() {
            // PHP: nation not found → delete event
            `when`(factionRepository.findById(anyLong())).thenReturn(java.util.Optional.empty())

            service.autoDeleteInvader(createWorld(), nationId = 99L, currentEventId = 42L)

            verify(eventRepository).deleteById(42L)
        }

        @Test
        fun `does not delete event when faction still exists and at war`() {
            val faction = createFaction(id = 1)
            faction.meta["atWar"] = true
            `when`(factionRepository.findById(anyLong())).thenReturn(java.util.Optional.of(faction))
            `when`(officerRepository.findByFactionId(anyLong())).thenReturn(emptyList())

            service.autoDeleteInvader(createWorld(), nationId = 1L, currentEventId = 5L)

            verify(eventRepository, never()).deleteById(anyLong())
        }
    }

    // ─── RegNPC ────────────────────────────────────────────────────────────

    @Nested
    inner class RegNPC {

        @Test
        fun `creates an officer with specified stats`() {
            // PHP: insert general with name, nationId, leadership, strength, intel
            `when`(planetRepository.findBySessionId(1L)).thenReturn(emptyList())
            val officerCaptor = ArgumentCaptor.forClass(Officer::class.java)
            `when`(officerRepository.save(officerCaptor.capture())).thenAnswer { it.arguments[0] }

            val params = mapOf(
                "name" to "테스트장수",
                "nationId" to 2,
                "leadership" to 80,
                "strength" to 70,
                "intel" to 60,
            )
            service.regNPC(createWorld(), params)

            val saved = officerCaptor.value
            assertEquals("테스트장수", saved.name)
            assertEquals(2L, saved.factionId)
            assertEquals(80.toShort(), saved.leadership)
            assertEquals(70.toShort(), saved.command)
            assertEquals(60.toShort(), saved.intelligence)
        }

        @Test
        fun `regNeutralNPC creates officer with npcState 6`() {
            `when`(planetRepository.findBySessionId(1L)).thenReturn(emptyList())
            val officerCaptor = ArgumentCaptor.forClass(Officer::class.java)
            `when`(officerRepository.save(officerCaptor.capture())).thenAnswer { it.arguments[0] }

            service.regNeutralNPC(createWorld(), mapOf("name" to "중립NPC"))

            val saved = officerCaptor.value
            assertEquals(6.toShort(), saved.npcState)
        }

        @Test
        fun `resolves planet by name when planetId not specified`() {
            val planet = createPlanet(id = 5L)
            planet.name = "낙양"
            `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))
            val officerCaptor = ArgumentCaptor.forClass(Officer::class.java)
            `when`(officerRepository.save(officerCaptor.capture())).thenAnswer { it.arguments[0] }

            service.regNPC(createWorld(), mapOf("name" to "장수", "city" to "낙양"))

            val saved = officerCaptor.value
            assertEquals(5L, saved.planetId)
        }
    }

    // ─── LostUniqueItem ────────────────────────────────────────────────────

    @Nested
    inner class LostUniqueItem {

        @Test
        fun `does not affect officers with no items`() {
            val off = createOfficer(npcState = 1, accessoryCode = "None")
            `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(off))

            service.lostUniqueItem(createWorld(), lostProb = 1.0)

            verify(officerRepository, never()).save(anyNonNull())
            assertEquals("None", off.accessoryCode)
        }

        @Test
        fun `does not affect NPC officers (npcState gt 1)`() {
            val off = createOfficer(npcState = 3, accessoryCode = "전국옥새")
            `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(off))

            service.lostUniqueItem(createWorld(), lostProb = 1.0)

            // npcState=3 > 1 → skipped entirely
            assertEquals("전국옥새", off.accessoryCode)
        }

        @Test
        fun `does not remove buyable items even at 100 percent probability`() {
            val off = createOfficer(npcState = 0, accessoryCode = "숫돌")
            `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(off))

            service.lostUniqueItem(createWorld(), lostProb = 1.0)

            assertEquals("숫돌", off.accessoryCode)
        }
    }

    // ─── MergeInheritPointRank ─────────────────────────────────────────────

    @Nested
    inner class MergeInheritPointRank {

        @Test
        fun `deletes old merge entries and creates new ones per officer`() {
            val off = createOfficer(id = 1, leadership = 80, command = 70, intelligence = 60, experience = 1000, dedication = 500)
            `when`(officerRepository.findBySessionId(anyLong())).thenReturn(listOf(off))
            `when`(rankDataRepository.findBySessionIdAndCategory(anyLong(), anyString())).thenReturn(emptyList())
            `when`(rankDataRepository.saveAll(anyNonNull<List<RankData>>())).thenReturn(emptyList())

            assertDoesNotThrow { service.mergeInheritPointRank(createWorld()) }

            // Should save new entries for the officer
            verify(rankDataRepository, atLeastOnce()).saveAll(anyNonNull<List<RankData>>())
        }

        @Test
        fun `calculates positive inheritance score from officer stats`() {
            val off = createOfficer(leadership = 80, command = 70, intelligence = 60, experience = 2000, dedication = 1000)
            `when`(officerRepository.findBySessionId(anyLong())).thenReturn(listOf(off))
            `when`(rankDataRepository.findBySessionIdAndCategory(anyLong(), anyString())).thenReturn(emptyList())
            `when`(rankDataRepository.saveAll(anyNonNull<List<RankData>>())).thenReturn(emptyList())

            service.mergeInheritPointRank(createWorld())

            // Verify saveAll is called (score is sum of stats + experience/100 + dedication/100)
            verify(rankDataRepository, atLeastOnce()).saveAll(anyNonNull<List<RankData>>())
        }
    }

    // ─── CreateManyNPC ─────────────────────────────────────────────────────

    @Nested
    inner class CreateManyNPC {

        @Test
        fun `creates specified number of NPCs`() {
            val planet = createPlanet(factionId = 0)
            `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))
            `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())
            val savedOfficers = mutableListOf<Officer>()
            `when`(officerRepository.save(anyNonNull<Officer>())).thenAnswer {
                val g = it.arguments[0] as Officer
                savedOfficers.add(g)
                g
            }

            service.createManyNPC(createWorld(), npcCount = 3, fillCnt = 0)

            assertEquals(3, savedOfficers.size)
        }

        @Test
        fun `skips when npcCount and fillCnt are both zero`() {
            `when`(planetRepository.findBySessionId(1L)).thenReturn(emptyList())
            `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

            service.createManyNPC(createWorld(), npcCount = 0, fillCnt = 0)

            verify(officerRepository, never()).save(anyNonNull())
        }

        @Test
        fun `created NPCs have npcState 3`() {
            val planet = createPlanet(factionId = 0)
            `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))
            `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())
            val officerCaptor = ArgumentCaptor.forClass(Officer::class.java)
            `when`(officerRepository.save(officerCaptor.capture())).thenAnswer { it.arguments[0] }

            service.createManyNPC(createWorld(), npcCount = 1, fillCnt = 0)

            assertEquals(3.toShort(), officerCaptor.value.npcState)
        }
    }

    // ─── AssignOfficerSpeciality ───────────────────────────────────────────

    @Nested
    inner class AssignOfficerSpeciality {

        @Test
        fun `skips assignment when world year is too early (less than startYear + 3)`() {
            // PHP: if year < startYear + 3, skip speciality assignment
            val world = createWorld(year = 200)
            world.config["startYear"] = 200  // 200 < 200+3=203, should skip

            assertDoesNotThrow { service.assignGeneralSpeciality(world) }

            verify(officerRepository, never()).findBySessionId(anyLong())
        }

        @Test
        fun `processes assignment when world year is sufficient (startYear + 3 or later)`() {
            // PHP: if year >= startYear + 3, run assignment
            val world = createWorld(year = 205)
            world.config["startYear"] = 200  // 205 >= 203
            val off = createOfficer()
            `when`(officerRepository.findBySessionId(anyLong())).thenReturn(listOf(off))
            `when`(officerRepository.saveAll(anyNonNull<List<Officer>>())).thenReturn(listOf(off))

            // Just verify it runs without exception and calls the repository
            assertDoesNotThrow { service.assignGeneralSpeciality(world) }

            verify(officerRepository).findBySessionId(1L)
        }
    }
}
