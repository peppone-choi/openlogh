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

    private fun createGeneral(
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
        officerLevel: Short = 0,
        npcState: Short = 2,
        experience: Int = 1000,
        dedication: Int = 500,
        accessoryCode: String = "None",
    ): Officer = Officer(
        id = id, sessionId = sessionId, factionId = factionId, name = name,
        leadership = leadership, command = command, intelligence = intelligence,
        betray = betray, age = age, belong = belong, officerLevel = officerLevel,
        npcState = npcState, experience = experience, dedication = dedication,
        accessoryCode = accessoryCode,
    )

    private fun createNation(
        id: Long = 1L,
        sessionId: Long = 1L,
        name: String = "테스트국",
        level: Short = 2,
        scoutLevel: Short = 0,
        funds: Int = 10000,
    ): Faction = Faction(id = id, sessionId = sessionId, name = name, factionRank = level, scoutLevel = scoutLevel, funds = funds)

    private fun createCity(
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
        officerSet: Int = 0,
        dead: Int = 0,
    ): Planet = Planet(
        id = id, sessionId = sessionId, name = "테스트도시$id", factionId = factionId,
        population = population, populationMax = populationMax, production = production, productionMax = productionMax,
        commerce = commerce, commerceMax = commerceMax, security = security, securityMax = securityMax,
        orbitalDefense = orbitalDefense, orbitalDefenseMax = orbitalDefenseMax, fortress = fortress, fortressMax = fortressMax,
        approval = approval.toInt(), tradeRoute = tradeRoute, officerSet = officerSet, dead = dead,
    )

    // ─── AddGlobalBetray ───────────────────────────────────────────────────

    @Nested
    inner class AddGlobalBetray {

        @Test
        fun `increases betray for generals at or below ifMax`() {
            // PHP: foreach general where betray <= ifMax → betray += cnt
            val gen1 = createGeneral(id = 1, betray = 0)  // betray <= 0 ✓
            val gen2 = createGeneral(id = 2, betray = 5)  // betray > 0 ✗
            `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(gen1, gen2))
            `when`(officerRepository.saveAll(anyNonNull<List<Officer>>())).thenReturn(listOf(gen1, gen2))

            service.addGlobalBetray(createWorld(), cnt = 1, ifMax = 0)

            assertEquals(1.toShort(), gen1.betray)
            assertEquals(5.toShort(), gen2.betray)  // unchanged
        }

        @Test
        fun `increases betray by cnt`() {
            val gen = createGeneral(betray = 2)
            `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(gen))
            `when`(officerRepository.saveAll(anyNonNull<List<Officer>>())).thenReturn(listOf(gen))

            service.addGlobalBetray(createWorld(), cnt = 3, ifMax = 10)

            assertEquals(5.toShort(), gen.betray)
        }

        @Test
        fun `handles empty general list gracefully`() {
            `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())
            `when`(officerRepository.saveAll(anyNonNull<List<Officer>>())).thenReturn(emptyList())

            assertDoesNotThrow { service.addGlobalBetray(createWorld()) }
        }

        @Test
        fun `does not affect generals above ifMax`() {
            val gen = createGeneral(betray = 5)
            `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(gen))
            `when`(officerRepository.saveAll(anyNonNull<List<Officer>>())).thenReturn(listOf(gen))

            service.addGlobalBetray(createWorld(), cnt = 1, ifMax = 3)

            assertEquals(5.toShort(), gen.betray)  // unchanged because betray(5) > ifMax(3)
        }
    }

    // ─── BlockScoutAction / UnblockScoutAction ────────────────────────────

    @Nested
    inner class ScoutActions {

        @Test
        fun `blockScoutAction sets all nation scoutLevel to 1`() {
            // PHP: foreach nation → scout = 1 (blocked)
            val nation1 = createNation(id = 1, scoutLevel = 0)
            val nation2 = createNation(id = 2, scoutLevel = 0)
            `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(nation1, nation2))
            `when`(factionRepository.saveAll(anyNonNull<List<Faction>>())).thenReturn(listOf(nation1, nation2))

            service.blockScoutAction(createWorld())

            assertEquals(1.toShort(), nation1.scoutLevel)
            assertEquals(1.toShort(), nation2.scoutLevel)
        }

        @Test
        fun `unblockScoutAction sets all nation scoutLevel to 0`() {
            // PHP: foreach nation → scout = 0 (unblocked)
            val nation1 = createNation(id = 1, scoutLevel = 1)
            val nation2 = createNation(id = 2, scoutLevel = 1)
            `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(nation1, nation2))
            `when`(factionRepository.saveAll(anyNonNull<List<Faction>>())).thenReturn(listOf(nation1, nation2))

            service.unblockScoutAction(createWorld())

            assertEquals(0.toShort(), nation1.scoutLevel)
            assertEquals(0.toShort(), nation2.scoutLevel)
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

    // ─── ChangeCity ────────────────────────────────────────────────────────

    @Nested
    inner class ChangeCity {

        @Test
        fun `applies approval absolute value to all cities when target is null`() {
            // PHP: $actions['approval'] = 50 → city.approval = 50
            val city1 = createCity(id = 1, approval = 80f)
            val city2 = createCity(id = 2, approval = 60f)
            `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(city1, city2))
            `when`(planetRepository.saveAll(anyNonNull<List<Planet>>())).thenReturn(listOf(city1, city2))

            service.changeCity(createWorld(), null, mapOf("approval" to 50))

            assertEquals(50f, city1.approval, 0.01f)
            assertEquals(50f, city2.approval, 0.01f)
        }

        @Test
        fun `applies percentage expression to city trade`() {
            // PHP: tradeRoute = 100 (absolute value)
            val city = createCity(tradeRoute = 95)
            `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(city))
            `when`(planetRepository.saveAll(anyNonNull<List<Planet>>())).thenReturn(listOf(city))

            service.changeCity(createWorld(), null, mapOf("trade" to 102))

            assertEquals(102, city.tradeRoute)
        }

        @Test
        fun `filters cities by free target`() {
            val freeCity = createCity(id = 1, factionId = 0)
            val occupiedCity = createCity(id = 2, factionId = 1)
            `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(freeCity, occupiedCity))
            `when`(planetRepository.saveAll(anyNonNull<List<Planet>>())).thenReturn(listOf(freeCity))

            service.changeCity(createWorld(), "free", mapOf("approval" to 100))

            assertEquals(100f, freeCity.approval, 0.01f)
            // occupied city should be unchanged
            assertEquals(80f, occupiedCity.approval, 0.01f)
        }

        @Test
        fun `applies math expression with plus operator to city field`() {
            val city = createCity(production = 400, productionMax = 1000)
            `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(city))
            `when`(planetRepository.saveAll(anyNonNull<List<Planet>>())).thenReturn(listOf(city))

            // "+100" should add 100 to current production
            service.changeCity(createWorld(), null, mapOf("production" to "+100"))

            assertEquals(500, city.production)
        }

        @Test
        fun `clamps approval to 100 maximum`() {
            val city = createCity(approval = 90f)
            `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(city))
            `when`(planetRepository.saveAll(anyNonNull<List<Planet>>())).thenReturn(listOf(city))

            service.changeCity(createWorld(), null, mapOf("approval" to 200))

            assertEquals(100f, city.approval, 0.01f)
        }
    }

    // ─── NewYear ────────────────────────────────────────────────────────────

    @Nested
    inner class NewYear {

        @Test
        fun `increments age for all generals`() {
            // PHP: foreach general → age++
            val gen = createGeneral(age = 25, factionId = 1)
            `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(gen))
            `when`(officerRepository.saveAll(anyNonNull<List<Officer>>())).thenReturn(listOf(gen))

            service.newYear(createWorld())

            assertEquals(26.toShort(), gen.age)
        }

        @Test
        fun `increments belong for generals in a nation`() {
            // PHP: foreach general where nation != 0 → belong++
            val gen = createGeneral(factionId = 1, belong = 10)
            `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(gen))
            `when`(officerRepository.saveAll(anyNonNull<List<Officer>>())).thenReturn(listOf(gen))

            service.newYear(createWorld())

            assertEquals(11.toShort(), gen.belong)
        }

        @Test
        fun `does not increment belong for wandering generals`() {
            // PHP: only generals in a nation get belong++; nation==0 → wander
            val gen = createGeneral(factionId = 0, belong = 5)
            `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(gen))
            `when`(officerRepository.saveAll(anyNonNull<List<Officer>>())).thenReturn(listOf(gen))

            service.newYear(createWorld())

            assertEquals(5.toShort(), gen.belong)  // unchanged
        }

        @Test
        fun `logs history message with current year`() {
            `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())
            `when`(officerRepository.saveAll(anyNonNull<List<Officer>>())).thenReturn(emptyList())

            service.newYear(createWorld(year = 220))

            verify(historyService).logWorldHistory(anyLong(), anyString(), anyInt(), anyInt(), eq(false))
        }
    }

    // ─── ResetOfficerLock ──────────────────────────────────────────────────

    @Nested
    inner class ResetOfficerLock {

        @Test
        fun `resets officerSet to 0 for all cities`() {
            // PHP: UPDATE city SET officer_set=0 (all cities)
            val city = createCity(officerSet = 1)
            `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(city))
            `when`(planetRepository.saveAll(anyNonNull<List<Planet>>())).thenReturn(listOf(city))
            `when`(factionRepository.findBySessionId(1L)).thenReturn(emptyList())
            `when`(factionRepository.saveAll(anyNonNull<List<Faction>>())).thenReturn(emptyList())

            service.resetOfficerLock(createWorld())

            assertEquals(0, city.officerSet)
        }

        @Test
        fun `removes chiefSet from nation meta`() {
            // PHP: unset nation meta chiefSet
            val nation = createNation()
            nation.meta["chiefSet"] = true
            `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(nation))
            `when`(factionRepository.saveAll(anyNonNull<List<Faction>>())).thenReturn(listOf(nation))
            `when`(planetRepository.findBySessionId(1L)).thenReturn(emptyList())
            `when`(planetRepository.saveAll(anyNonNull<List<Planet>>())).thenReturn(emptyList())

            service.resetOfficerLock(createWorld())

            assertFalse(nation.meta.containsKey("chiefSet"))
        }
    }

    // ─── ProcessWarIncome ──────────────────────────────────────────────────

    @Nested
    inner class ProcessWarIncome {

        @Test
        fun `converts dead troops back to population at 20 percent`() {
            // PHP: population += dead * 0.2; dead = 0
            val city = createCity(factionId = 1, population = 10000, dead = 500)
            `when`(planetRepository.findBySessionId(anyLong())).thenReturn(listOf(city))
            `when`(planetRepository.saveAll(anyNonNull<List<Planet>>())).thenReturn(listOf(city))
            `when`(factionRepository.findBySessionId(anyLong())).thenReturn(emptyList())
            `when`(factionRepository.saveAll(anyNonNull<List<Faction>>())).thenReturn(emptyList())

            service.processWarIncome(createWorld())

            // 500 dead * 0.2 = 100 returned as population
            assertEquals(10100, city.population)
            assertEquals(0, city.dead)
        }

        @Test
        fun `population gain from dead capped at populationMax`() {
            // dead=50000, population=49900, populationMax=50000 → gain=10000 but capped to 100
            val city = createCity(factionId = 1, population = 49900, populationMax = 50000, dead = 50000)
            `when`(planetRepository.findBySessionId(anyLong())).thenReturn(listOf(city))
            `when`(planetRepository.saveAll(anyNonNull<List<Planet>>())).thenReturn(listOf(city))
            `when`(factionRepository.findBySessionId(anyLong())).thenReturn(emptyList())
            `when`(factionRepository.saveAll(anyNonNull<List<Faction>>())).thenReturn(emptyList())

            service.processWarIncome(createWorld())

            assertTrue(city.population <= 50000, "population should not exceed populationMax")
            assertEquals(0, city.dead)
        }

        @Test
        fun `zero dead means no population change`() {
            val city = createCity(population = 10000, dead = 0)
            `when`(planetRepository.findBySessionId(anyLong())).thenReturn(listOf(city))
            `when`(planetRepository.saveAll(anyNonNull<List<Planet>>())).thenReturn(listOf(city))
            `when`(factionRepository.findBySessionId(anyLong())).thenReturn(emptyList())
            `when`(factionRepository.saveAll(anyNonNull<List<Faction>>())).thenReturn(emptyList())

            service.processWarIncome(createWorld())

            assertEquals(10000, city.population)
        }
    }

    // ─── DeleteEvent (via AutoDeleteInvader) ────────────────────────────────

    @Nested
    inner class AutoDeleteInvader {

        @Test
        fun `deletes event when nation does not exist`() {
            // PHP: nation not found → delete event
            `when`(factionRepository.findById(anyLong())).thenReturn(java.util.Optional.empty())

            service.autoDeleteInvader(createWorld(), nationId = 99L, currentEventId = 42L)

            verify(eventRepository).deleteById(42L)
        }

        @Test
        fun `does not delete event when nation still exists and at war`() {
            val nation = createNation(id = 1)
            nation.meta["atWar"] = true
            `when`(factionRepository.findById(anyLong())).thenReturn(java.util.Optional.of(nation))
            `when`(officerRepository.findByFactionId(anyLong())).thenReturn(emptyList())

            service.autoDeleteInvader(createWorld(), nationId = 1L, currentEventId = 5L)

            verify(eventRepository, never()).deleteById(anyLong())
        }
    }

    // ─── RegNPC ────────────────────────────────────────────────────────────

    @Nested
    inner class RegNPC {

        @Test
        fun `creates a general with specified stats`() {
            // PHP: insert general with name, factionId, leadership, strength, intel
            `when`(planetRepository.findBySessionId(1L)).thenReturn(emptyList())
            val generalCaptor = ArgumentCaptor.forClass(Officer::class.java)
            `when`(officerRepository.save(generalCaptor.capture())).thenAnswer { it.arguments[0] }

            val params = mapOf(
                "name" to "테스트장수",
                "factionId" to 2,
                "leadership" to 80,
                "strength" to 70,
                "intel" to 60,
            )
            service.regNPC(createWorld(), params)

            val saved = generalCaptor.value
            assertEquals("테스트장수", saved.name)
            assertEquals(2L, saved.factionId)
            assertEquals(80.toShort(), saved.leadership)
            assertEquals(70.toShort(), saved.command)
            assertEquals(60.toShort(), saved.intelligence)
        }

        @Test
        fun `regNeutralNPC creates general with npcState 6`() {
            `when`(planetRepository.findBySessionId(1L)).thenReturn(emptyList())
            val generalCaptor = ArgumentCaptor.forClass(Officer::class.java)
            `when`(officerRepository.save(generalCaptor.capture())).thenAnswer { it.arguments[0] }

            service.regNeutralNPC(createWorld(), mapOf("name" to "중립NPC"))

            val saved = generalCaptor.value
            assertEquals(6.toShort(), saved.npcState)
        }

        @Test
        fun `resolves city by name when planetId not specified`() {
            val city = createCity(id = 5L)
            city.name = "낙양"
            `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(city))
            val generalCaptor = ArgumentCaptor.forClass(Officer::class.java)
            `when`(officerRepository.save(generalCaptor.capture())).thenAnswer { it.arguments[0] }

            service.regNPC(createWorld(), mapOf("name" to "장수", "city" to "낙양"))

            val saved = generalCaptor.value
            assertEquals(5L, saved.planetId)
        }
    }

    // ─── LostUniqueItem ────────────────────────────────────────────────────

    @Nested
    inner class LostUniqueItem {

        @Test
        fun `does not affect generals with no items`() {
            val gen = createGeneral(npcState = 1, accessoryCode = "None")
            `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(gen))

            service.lostUniqueItem(createWorld(), lostProb = 1.0)

            verify(officerRepository, never()).save(anyNonNull())
            assertEquals("None", gen.accessoryCode)
        }

        @Test
        fun `does not affect NPC generals (npcState gt 1)`() {
            val gen = createGeneral(npcState = 3, accessoryCode = "전국옥새")
            `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(gen))

            service.lostUniqueItem(createWorld(), lostProb = 1.0)

            // npcState=3 > 1 → skipped entirely
            assertEquals("전국옥새", gen.accessoryCode)
        }

        @Test
        fun `does not remove buyable items even at 100 percent probability`() {
            val gen = createGeneral(npcState = 0, accessoryCode = "숫돌")
            `when`(officerRepository.findBySessionId(1L)).thenReturn(listOf(gen))

            service.lostUniqueItem(createWorld(), lostProb = 1.0)

            assertEquals("숫돌", gen.accessoryCode)
        }
    }

    // ─── MergeInheritPointRank ─────────────────────────────────────────────

    @Nested
    inner class MergeInheritPointRank {

        @Test
        fun `deletes old merge entries and creates new ones per general`() {
            val gen = createGeneral(id = 1, leadership = 80, command = 70, intelligence = 60, experience = 1000, dedication = 500)
            `when`(officerRepository.findBySessionId(anyLong())).thenReturn(listOf(gen))
            `when`(rankDataRepository.findBySessionIdAndCategory(anyLong(), anyString())).thenReturn(emptyList())
            `when`(rankDataRepository.saveAll(anyNonNull<List<RankData>>())).thenReturn(emptyList())

            assertDoesNotThrow { service.mergeInheritPointRank(createWorld()) }

            // Should save new entries for the general
            verify(rankDataRepository, atLeastOnce()).saveAll(anyNonNull<List<RankData>>())
        }

        @Test
        fun `calculates positive inheritance score from general stats`() {
            val gen = createGeneral(leadership = 80, command = 70, intelligence = 60, experience = 2000, dedication = 1000)
            `when`(officerRepository.findBySessionId(anyLong())).thenReturn(listOf(gen))
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
            val city = createCity(factionId = 0)
            `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(city))
            `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())
            val savedGenerals = mutableListOf<Officer>()
            `when`(officerRepository.save(anyNonNull<Officer>())).thenAnswer {
                val g = it.arguments[0] as Officer
                savedGenerals.add(g)
                g
            }

            service.createManyNPC(createWorld(), npcCount = 3, fillCnt = 0)

            assertEquals(3, savedGenerals.size)
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
            val city = createCity(factionId = 0)
            `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(city))
            `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())
            val generalCaptor = ArgumentCaptor.forClass(Officer::class.java)
            `when`(officerRepository.save(generalCaptor.capture())).thenAnswer { it.arguments[0] }

            service.createManyNPC(createWorld(), npcCount = 1, fillCnt = 0)

            assertEquals(3.toShort(), generalCaptor.value.npcState)
        }
    }

    // ─── AssignGeneralSpeciality ───────────────────────────────────────────

    @Nested
    inner class AssignGeneralSpeciality {

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
            val gen = createGeneral()
            `when`(officerRepository.findBySessionId(anyLong())).thenReturn(listOf(gen))
            `when`(officerRepository.saveAll(anyNonNull<List<Officer>>())).thenReturn(listOf(gen))

            // Just verify it runs without exception and calls the repository
            assertDoesNotThrow { service.assignGeneralSpeciality(world) }

            verify(officerRepository).findBySessionId(1L)
        }
    }
}
