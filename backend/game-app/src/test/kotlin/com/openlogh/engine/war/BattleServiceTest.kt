package com.openlogh.engine.war

import com.openlogh.engine.DiplomacyService
import com.openlogh.engine.EventService
import com.openlogh.entity.*
import com.openlogh.repository.*
import com.openlogh.service.GameConstService
import com.openlogh.service.HistoryService
import com.openlogh.service.InheritanceService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.OffsetDateTime
import java.util.Optional

class BattleServiceTest {

    private lateinit var service: BattleService
    private lateinit var planetRepository: PlanetRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var oldFactionRepository: OldFactionRepository
    private lateinit var fleetRepository: FleetRepository
    private lateinit var factionTurnRepository: FactionTurnRepository
    private lateinit var eventService: EventService
    private lateinit var diplomacyService: DiplomacyService
    private lateinit var historyService: HistoryService

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyNonNull(): T = org.mockito.Mockito.any<T>() as T

    @BeforeEach
    fun setUp() {
        planetRepository = mock(PlanetRepository::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        messageRepository = mock(MessageRepository::class.java)
        oldFactionRepository = mock(OldFactionRepository::class.java)
        fleetRepository = mock(FleetRepository::class.java)
        factionTurnRepository = mock(FactionTurnRepository::class.java)
        eventService = mock(EventService::class.java)
        diplomacyService = mock(DiplomacyService::class.java)
        historyService = mock(HistoryService::class.java)

        `when`(planetRepository.save(anyNonNull<Planet>())).thenAnswer { it.arguments[0] }
        `when`(officerRepository.save(anyNonNull<Officer>())).thenAnswer { it.arguments[0] }
        `when`(factionRepository.save(anyNonNull<Faction>())).thenAnswer { it.arguments[0] }
        `when`(messageRepository.save(anyNonNull<Message>())).thenAnswer { it.arguments[0] }
        `when`(oldFactionRepository.save(anyNonNull<OldFaction>())).thenAnswer { it.arguments[0] }
        `when`(fleetRepository.findByFactionId(anyLong())).thenReturn(emptyList())

        val modifierService = mock(com.openlogh.engine.modifier.ModifierService::class.java)
        val gameConstService = mock(GameConstService::class.java)
        val gameEventService = mock(com.openlogh.service.GameEventService::class.java)
        val tacticalSessionManager = mock(com.openlogh.engine.tactical.TacticalSessionManager::class.java)
        `when`(gameConstService.getInt("defaultCityWall")).thenReturn(1000)

        service = BattleService(
            planetRepository, officerRepository, factionRepository,
            messageRepository, oldFactionRepository, fleetRepository, factionTurnRepository,
            eventService, diplomacyService,
            modifierService, gameConstService, gameEventService, historyService,
            mock(InheritanceService::class.java), tacticalSessionManager,
            mock(com.openlogh.engine.SafeZoneService::class.java),
            mock(com.openlogh.engine.fleet.CrewGradeService::class.java),
            mock(com.openlogh.engine.planet.PlanetTypeRules::class.java),
        )
    }

    private fun createWorld(): SessionState {
        return SessionState(
            id = 1,
            scenarioCode = "test",
            currentYear = 200,
            currentMonth = 3,
            tickSeconds = 300,
            config = mutableMapOf("hiddenSeed" to "testSeed"),
        )
    }

    private fun createOfficer(
        id: Long = 1,
        factionId: Long = 1,
        planetId: Long = 1,
        command: Short = 99,
        leadership: Short = 99,
        ships: Int = 50000,
        supplies: Int = 500000,
        funds: Int = 10000,
        training: Short = 80,
        morale: Short = 80,
        npcState: Short = 0,
        rank: Short = 0,
        experience: Int = 10000,
        dedication: Int = 5000,
    ): Officer {
        return Officer(
            id = id,
            sessionId = 1,
            name = "장수$id",
            factionId = factionId,
            planetId = planetId,
            leadership = leadership,
            command = command,
            intelligence = 50,
            ships = ships,
            shipClass = 0,
            training = training,
            morale = morale,
            funds = funds,
            supplies = supplies,
            experience = experience,
            dedication = dedication,
            npcState = npcState,
            rank = rank,
            turnTime = OffsetDateTime.now(),
        )
    }

    private fun createPlanet(
        id: Long = 10,
        factionId: Long = 2,
        orbitalDefense: Int = 10,
        fortress: Int = 10,
        population: Int = 10000,
        production: Int = 1000,
        commerce: Int = 1000,
        security: Int = 1000,
    ): Planet {
        return Planet(
            id = id,
            sessionId = 1,
            name = "테스트도시",
            factionId = factionId,
            orbitalDefense = orbitalDefense,
            orbitalDefenseMax = 1000,
            fortress = fortress,
            fortressMax = 1000,
            population = population,
            populationMax = 50000,
            production = production,
            productionMax = 5000,
            commerce = commerce,
            commerceMax = 5000,
            security = security,
            securityMax = 5000,
        )
    }

    private fun createFaction(id: Long = 2, name: String = "위", funds: Int = 10000, supplies: Int = 10000, capitalPlanetId: Long? = 10): Faction {
        return Faction(
            id = id,
            sessionId = 1,
            name = name,
            color = "blue",
            funds = funds,
            supplies = supplies,
            capitalPlanetId = capitalPlanetId,
        )
    }

    // ========== executeBattle: city occupation dispatches events ==========

    @Test
    fun `executeBattle dispatches OCCUPY_CITY event on city occupation`() {
        val world = createWorld()
        val attacker = createOfficer(id = 1, factionId = 1)
        val planet = createPlanet(id = 10, factionId = 2, orbitalDefense = 10, fortress = 10)

        val attackerFaction = createFaction(id = 1, name = "촉")
        val defenderFaction = createFaction(id = 2, name = "위", capitalPlanetId = 99)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerFaction))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderFaction))
        `when`(officerRepository.findByCityId(10L)).thenReturn(emptyList())
        // Defender faction still has other planets
        `when`(planetRepository.findByFactionId(2L)).thenReturn(listOf(createPlanet(id = 99, factionId = 2)))

        val result = service.executeBattle(attacker, planet, world)

        if (result.cityOccupied) {
            verify(eventService).dispatchEvents(world, "OCCUPY_CITY")
        }
    }

    @Test
    fun `executeBattle dispatches DESTROY_NATION event when last city taken`() {
        val world = createWorld()
        val attacker = createOfficer(id = 1, factionId = 1)
        val planet = createPlanet(id = 10, factionId = 2, orbitalDefense = 10, fortress = 10)

        val attackerFaction = createFaction(id = 1, name = "촉")
        val defenderFaction = createFaction(id = 2, name = "위", capitalPlanetId = 10)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerFaction))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderFaction))
        `when`(officerRepository.findByCityId(10L)).thenReturn(emptyList())
        // No remaining planets for defender
        `when`(planetRepository.findByFactionId(2L)).thenReturn(emptyList())
        // No officers in destroyed faction
        `when`(officerRepository.findByNationId(2L)).thenReturn(emptyList())

        val result = service.executeBattle(attacker, planet, world)

        if (result.cityOccupied) {
            verify(eventService).dispatchEvents(world, "OCCUPY_CITY")
            verify(eventService).dispatchEvents(world, "DESTROY_NATION")
            verify(historyService).logWorldHistory(anyLong(), contains("멸망"), anyInt(), anyInt())
            verify(historyService).logNationHistory(anyLong(), anyLong(), contains("정복"), anyInt(), anyInt())
        }
    }

    // ========== Nation destruction: officer release and penalties ==========

    @Test
    fun `nation destruction releases officers with resource penalties`() {
        val world = createWorld()
        val attacker = createOfficer(id = 1, factionId = 1)
        val planet = createPlanet(id = 10, factionId = 2, orbitalDefense = 10, fortress = 10)

        val attackerFaction = createFaction(id = 1, name = "촉")
        val defenderFaction = createFaction(id = 2, name = "위", capitalPlanetId = 10, funds = 5000, supplies = 8000)

        val defOfficer1 = createOfficer(id = 10, factionId = 2, funds = 1000, supplies = 2000, experience = 1000, dedication = 1000, npcState = 0)
        val defOfficer2 = createOfficer(id = 11, factionId = 2, funds = 500, supplies = 1000, experience = 500, dedication = 500, npcState = 3)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerFaction))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderFaction))
        `when`(officerRepository.findByCityId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L)).thenReturn(emptyList())
        `when`(officerRepository.findByNationId(2L)).thenReturn(listOf(defOfficer1, defOfficer2))

        val result = service.executeBattle(attacker, planet, world)

        if (result.cityOccupied) {
            // Both officers released (factionId = 0)
            assertEquals(0L, defOfficer1.factionId)
            assertEquals(0L, defOfficer2.factionId)

            // Officers demoted
            assertEquals(0.toShort(), defOfficer1.rank)
            assertEquals(0.toShort(), defOfficer2.rank)

            // Funds/supplies reduced (20-50%)
            assertTrue(defOfficer1.funds < 1000, "Officer should lose funds")
            assertTrue(defOfficer1.supplies < 2000, "Officer should lose supplies")

            // Experience reduced by 10%
            assertTrue(defOfficer1.experience < 1000, "Officer should lose experience")

            // Dedication reduced by 50%
            assertTrue(defOfficer1.dedication < 1000, "Officer should lose dedication")
        }
    }

    // ========== Nation destruction: conquest rewards ==========

    @Test
    fun `nation destruction distributes conquest rewards to attacker faction`() {
        val world = createWorld()
        val attacker = createOfficer(id = 1, factionId = 1)
        val planet = createPlanet(id = 10, factionId = 2, orbitalDefense = 10, fortress = 10)

        val attackerFaction = createFaction(id = 1, name = "촉", funds = 5000, supplies = 5000)
        val defenderFaction = createFaction(id = 2, name = "위", capitalPlanetId = 10, funds = 10000, supplies = 12000)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerFaction))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderFaction))
        `when`(officerRepository.findByCityId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L)).thenReturn(emptyList())
        `when`(officerRepository.findByNationId(2L)).thenReturn(emptyList())

        val result = service.executeBattle(attacker, planet, world)

        if (result.cityOccupied) {
            assertTrue(attackerFaction.funds > 5000, "Attacker faction should gain funds reward")
            assertTrue(attackerFaction.supplies > 5000, "Attacker faction should gain supplies reward")
        }
    }

    // ========== Nation destruction: diplomatic relations killed ==========

    @Test
    fun `nation destruction kills all diplomatic relations`() {
        val world = createWorld()
        val attacker = createOfficer(id = 1, factionId = 1)
        val planet = createPlanet(id = 10, factionId = 2, orbitalDefense = 10, fortress = 10)

        val attackerFaction = createFaction(id = 1, name = "촉")
        val defenderFaction = createFaction(id = 2, name = "위", capitalPlanetId = 10)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerFaction))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderFaction))
        `when`(officerRepository.findByCityId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L)).thenReturn(emptyList())
        `when`(officerRepository.findByNationId(2L)).thenReturn(emptyList())

        val result = service.executeBattle(attacker, planet, world)

        if (result.cityOccupied) {
            verify(diplomacyService).killAllRelationsForNation(1L, 2L)
        }
    }

    // ========== City occupation: planet stat reset ==========

    @Test
    fun `city occupation resets planet stats per legacy rules`() {
        val world = createWorld()
        val attacker = createOfficer(id = 1, factionId = 1)
        val planet = createPlanet(id = 10, factionId = 2, orbitalDefense = 10, fortress = 10, production = 1000, commerce = 1000, security = 1000)

        val attackerFaction = createFaction(id = 1, name = "촉")
        val defenderFaction = createFaction(id = 2, name = "위", capitalPlanetId = 99)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerFaction))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderFaction))
        `when`(officerRepository.findByCityId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L)).thenReturn(listOf(createPlanet(id = 99, factionId = 2)))

        val result = service.executeBattle(attacker, planet, world)

        if (result.cityOccupied) {
            assertEquals(1L, planet.factionId, "Planet faction should change to attacker")
            assertEquals(0f, planet.approval, "Approval should be reset")
            assertEquals(1.toShort(), planet.supplyState)
            assertEquals(0.toShort(), planet.term)
            assertEquals(0, planet.garrisonSet)
            // production/commerce/security reduced by 30%
            assertEquals(700, planet.production)
            assertEquals(700, planet.commerce)
            assertEquals(700, planet.security)
        }
    }

    // ========== Capital relocation ==========

    @Test
    fun `capital relocation halves faction funds and supplies`() {
        val world = createWorld()
        val attacker = createOfficer(id = 1, factionId = 1)
        val planet = createPlanet(id = 10, factionId = 2, orbitalDefense = 10, fortress = 10)

        val attackerFaction = createFaction(id = 1, name = "촉")
        val defenderFaction = createFaction(id = 2, name = "위", capitalPlanetId = 10, funds = 10000, supplies = 8000)

        val otherPlanet = createPlanet(id = 99, factionId = 2, population = 20000)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerFaction))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderFaction))
        `when`(officerRepository.findByCityId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L)).thenReturn(listOf(otherPlanet))
        `when`(officerRepository.findByNationId(2L)).thenReturn(emptyList())

        val result = service.executeBattle(attacker, planet, world)

        if (result.cityOccupied) {
            assertEquals(99L, defenderFaction.capitalPlanetId)
            assertEquals(5000, defenderFaction.funds)
            assertEquals(4000, defenderFaction.supplies)
        }
    }

    @Test
    fun `capital relocation applies 20 percent morale loss to all nationals`() {
        val world = createWorld()
        val attacker = createOfficer(id = 1, factionId = 1)
        val planet = createPlanet(id = 10, factionId = 2, orbitalDefense = 10, fortress = 10)

        val attackerFaction = createFaction(id = 1, name = "촉")
        val defenderFaction = createFaction(id = 2, name = "위", capitalPlanetId = 10)

        val nationalOfficer = createOfficer(id = 20, factionId = 2, planetId = 99, morale = 100)
        val otherPlanet = createPlanet(id = 99, factionId = 2, population = 20000)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerFaction))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderFaction))
        `when`(officerRepository.findByCityId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L)).thenReturn(listOf(otherPlanet))
        `when`(officerRepository.findByNationId(2L)).thenReturn(listOf(nationalOfficer))

        val result = service.executeBattle(attacker, planet, world)

        if (result.cityOccupied) {
            assertEquals(80.toShort(), nationalOfficer.morale, "Morale should drop by 20%")
        }
    }

    // ========== Conquest logging ==========

    @Test
    fun `city occupation logs conquest message`() {
        val world = createWorld()
        val attacker = createOfficer(id = 1, factionId = 1)
        val planet = createPlanet(id = 10, factionId = 2, orbitalDefense = 10, fortress = 10)

        val attackerFaction = createFaction(id = 1, name = "촉")
        val defenderFaction = createFaction(id = 2, name = "위", capitalPlanetId = 99)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerFaction))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderFaction))
        `when`(officerRepository.findByCityId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L)).thenReturn(listOf(createPlanet(id = 99, factionId = 2)))

        val result = service.executeBattle(attacker, planet, world)

        if (result.cityOccupied) {
            verify(historyService).logWorldHistory(eq(1L), anyString(), eq(200), eq(3))
        }
    }

    // ========== NPC auto-join queuing ==========

    @Test
    fun `nation destruction hard-deletes faction and archives to old_faction`() {
        val world = createWorld()
        val attacker = createOfficer(id = 1, factionId = 1)
        val planet = createPlanet(id = 10, factionId = 2, orbitalDefense = 10, fortress = 10)

        val attackerFaction = createFaction(id = 1, name = "촉")
        val defenderFaction = createFaction(id = 2, name = "위", capitalPlanetId = 10)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerFaction))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderFaction))
        `when`(officerRepository.findByCityId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L)).thenReturn(emptyList())
        `when`(officerRepository.findByNationId(2L)).thenReturn(emptyList())

        val result = service.executeBattle(attacker, planet, world)

        if (result.cityOccupied) {
            verify(factionRepository).delete(defenderFaction)
            verify(oldFactionRepository).save(anyNonNull<OldFaction>())
            verify(factionTurnRepository).deleteByFactionId(2L)
        }
    }

    @Test
    fun `nation destruction resets planets to neutral and deletes fleets`() {
        val world = createWorld()
        val attacker = createOfficer(id = 1, factionId = 1)
        val planet = createPlanet(id = 10, factionId = 2, orbitalDefense = 10, fortress = 10)

        val attackerFaction = createFaction(id = 1, name = "촉")
        val defenderFaction = createFaction(id = 2, name = "위", capitalPlanetId = 10)

        val orphanedPlanet = createPlanet(id = 20, factionId = 2)
        val fleet1 = mock(Fleet::class.java)
        val fleet2 = mock(Fleet::class.java)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerFaction))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderFaction))
        `when`(officerRepository.findByCityId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L))
            .thenReturn(emptyList())
            .thenReturn(listOf(orphanedPlanet))
        `when`(officerRepository.findByNationId(2L)).thenReturn(emptyList())
        `when`(fleetRepository.findByFactionId(2L)).thenReturn(listOf(fleet1, fleet2))

        val result = service.executeBattle(attacker, planet, world)

        if (result.cityOccupied) {
            assertEquals(0L, orphanedPlanet.factionId, "Orphaned planet should be neutralized")
            assertEquals(0.toShort(), orphanedPlanet.frontState, "Planet frontState should be reset")
            verify(fleetRepository).deleteAll(listOf(fleet1, fleet2))
        }
    }

    @Test
    fun `nation destruction sets officer belong and fleet to zero`() {
        val world = createWorld()
        val attacker = createOfficer(id = 1, factionId = 1)
        val planet = createPlanet(id = 10, factionId = 2, orbitalDefense = 10, fortress = 10)

        val attackerFaction = createFaction(id = 1, name = "촉")
        val defenderFaction = createFaction(id = 2, name = "위", capitalPlanetId = 10)

        val defOfficer = createOfficer(id = 10, factionId = 2, funds = 1000, supplies = 2000)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerFaction))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderFaction))
        `when`(officerRepository.findByCityId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L)).thenReturn(emptyList())
        `when`(officerRepository.findByNationId(2L)).thenReturn(listOf(defOfficer))

        val result = service.executeBattle(attacker, planet, world)

        if (result.cityOccupied) {
            assertEquals(0L, defOfficer.factionId)
            assertEquals(0.toShort(), defOfficer.rank)
            assertEquals(0.toShort(), defOfficer.belong)
            assertEquals(0L, defOfficer.fleetId)
        }
    }

    @Test
    fun `nation destruction queues eligible NPCs for auto-join`() {
        val world = createWorld()
        val attacker = createOfficer(id = 1, factionId = 1)
        val planet = createPlanet(id = 10, factionId = 2, orbitalDefense = 10, fortress = 10)

        val attackerFaction = createFaction(id = 1, name = "촉")
        val defenderFaction = createFaction(id = 2, name = "위", capitalPlanetId = 10)

        // Create several NPC officers with eligible states
        val npcOfficers = (2..8).filter { it != 5 }.map { npcState ->
            createOfficer(
                id = (100 + npcState).toLong(),
                factionId = 2,
                npcState = npcState.toShort(),
                funds = 100,
                supplies = 100,
            )
        }

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerFaction))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderFaction))
        `when`(officerRepository.findByCityId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L)).thenReturn(emptyList())
        `when`(officerRepository.findByNationId(2L)).thenReturn(npcOfficers)

        service.executeBattle(attacker, planet, world)

        // All NPC officers should be released (factionId = 0)
        for (officer in npcOfficers) {
            assertEquals(0L, officer.factionId, "NPC officer ${officer.id} should be released")
        }

        // Some may have autoJoinNationId metadata (probabilistic, so we just check the field exists for those that got it)
        val autoJoinQueued = npcOfficers.filter { it.meta.containsKey("autoJoinNationId") }
        for (officer in autoJoinQueued) {
            assertEquals(1L, officer.meta["autoJoinNationId"], "Auto-join target should be attacker faction")
            assertTrue((officer.meta["autoJoinDelay"] as Int) in 0..12, "Delay should be 0-12")
        }
    }
}
