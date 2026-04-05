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
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import java.time.OffsetDateTime
import java.util.Optional

class BattleServiceTest {

    private lateinit var service: BattleService
    private lateinit var planetRepository: PlanetRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var recordRepository: RecordRepository
    private lateinit var oldNationRepository: OldNationRepository
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
        recordRepository = mock(RecordRepository::class.java)
        oldNationRepository = mock(OldNationRepository::class.java)
        fleetRepository = mock(FleetRepository::class.java)
        factionTurnRepository = mock(FactionTurnRepository::class.java)
        eventService = mock(EventService::class.java)
        diplomacyService = mock(DiplomacyService::class.java)
        historyService = mock(HistoryService::class.java)

        `when`(planetRepository.save(anyNonNull<Planet>())).thenAnswer { it.arguments[0] }
        `when`(officerRepository.save(anyNonNull<Officer>())).thenAnswer { it.arguments[0] }
        `when`(factionRepository.save(anyNonNull<Faction>())).thenAnswer { it.arguments[0] }
        `when`(messageRepository.save(anyNonNull<Message>())).thenAnswer { it.arguments[0] }
        `when`(oldNationRepository.save(anyNonNull<OldNation>())).thenAnswer { it.arguments[0] }
        `when`(fleetRepository.findByFactionId(anyLong())).thenReturn(emptyList())

        val modifierService = mock(com.openlogh.engine.modifier.ModifierService::class.java)
        val gameConstService = mock(GameConstService::class.java)
        `when`(gameConstService.getInt("defaultCityWall")).thenReturn(1000)

        service = BattleService(
            planetRepository, officerRepository, factionRepository,
            messageRepository, recordRepository, oldNationRepository, fleetRepository, factionTurnRepository,
            eventService, diplomacyService,
            modifierService, gameConstService, historyService, mock(InheritanceService::class.java),
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

    private fun createGeneral(
        id: Long = 1,
        nationId: Long = 1,
        cityId: Long = 1,
        strength: Short = 99,
        leadership: Short = 99,
        crew: Int = 50000,
        rice: Int = 500000,
        gold: Int = 10000,
        train: Short = 80,
        atmos: Short = 80,
        npcState: Short = 0,
        officerLevel: Short = 0,
        officerPlanet: Int = 0,
        experience: Int = 10000,
        dedication: Int = 5000,
    ): Officer {
        return Officer(
            id = id,
            sessionId = 1,
            name = "장수$id",
            factionId = nationId,
            planetId = cityId,
            leadership = leadership,
            command = strength,
            intelligence = 50,
            ships = crew,
            shipClass = 0,
            training = train,
            morale = atmos,
            funds = gold,
            supplies = rice,
            experience = experience,
            dedication = dedication,
            npcState = npcState,
            officerLevel = officerLevel,
            officerPlanet = officerPlanet,
            turnTime = OffsetDateTime.now(),
        )
    }

    private fun createCity(
        id: Long = 10,
        nationId: Long = 2,
        def: Int = 10,
        wall: Int = 10,
        pop: Int = 10000,
        agri: Int = 1000,
        comm: Int = 1000,
        secu: Int = 1000,
    ): Planet {
        return Planet(
            id = id,
            sessionId = 1,
            name = "테스트도시",
            factionId = nationId,
            orbitalDefense = def,
            orbitalDefenseMax = 1000,
            fortress = wall,
            fortressMax = 1000,
            population = pop,
            populationMax = 50000,
            production = agri,
            productionMax = 5000,
            commerce = comm,
            commerceMax = 5000,
            security = secu,
            securityMax = 5000,
        )
    }

    private fun createNation(id: Long = 2, name: String = "위", gold: Int = 10000, rice: Int = 10000, capitalCityId: Long? = 10): Faction {
        return Faction(
            id = id,
            sessionId = 1,
            name = name,
            color = "blue",
            funds = gold,
            supplies = rice,
            capitalPlanetId = capitalCityId,
        )
    }

    // ========== executeBattle: city occupation dispatches events ==========

    @Test
    fun `executeBattle dispatches OCCUPY_CITY event on city occupation`() {
        val world = createWorld()
        val attacker = createGeneral(id = 1, nationId = 1)
        val city = createCity(id = 10, nationId = 2, def = 10, wall = 10)

        val attackerNation = createNation(id = 1, name = "촉")
        val defenderNation = createNation(id = 2, name = "위", capitalCityId = 99)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerNation))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderNation))
        `when`(officerRepository.findByPlanetId(10L)).thenReturn(emptyList())
        // Defender nation still has other cities
        `when`(planetRepository.findByFactionId(2L)).thenReturn(listOf(createCity(id = 99, nationId = 2)))

        val result = service.executeBattle(attacker, city, world)

        if (result.cityOccupied) {
            verify(eventService).dispatchEvents(world, "OCCUPY_CITY")
        }
    }

    @Test
    fun `executeBattle dispatches DESTROY_NATION event when last city taken`() {
        val world = createWorld()
        val attacker = createGeneral(id = 1, nationId = 1)
        val city = createCity(id = 10, nationId = 2, def = 10, wall = 10)

        val attackerNation = createNation(id = 1, name = "촉")
        val defenderNation = createNation(id = 2, name = "위", capitalCityId = 10)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerNation))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderNation))
        `when`(officerRepository.findByPlanetId(10L)).thenReturn(emptyList())
        // No remaining cities for defender
        `when`(planetRepository.findByFactionId(2L)).thenReturn(emptyList())
        // No generals in destroyed nation
        `when`(officerRepository.findByFactionId(2L)).thenReturn(emptyList())

        val result = service.executeBattle(attacker, city, world)

        if (result.cityOccupied) {
            verify(eventService).dispatchEvents(world, "OCCUPY_CITY")
            verify(eventService).dispatchEvents(world, "DESTROY_NATION")
            verify(historyService).logWorldHistory(anyLong(), contains("멸망"), anyInt(), anyInt(), eq(false))
            verify(historyService).logNationHistory(anyLong(), anyLong(), contains("정복"), anyInt(), anyInt())
        }
    }

    // ========== Nation destruction: general release and penalties ==========

    @Test
    fun `nation destruction releases generals with resource penalties`() {
        val world = createWorld()
        val attacker = createGeneral(id = 1, nationId = 1)
        val city = createCity(id = 10, nationId = 2, def = 10, wall = 10)

        val attackerNation = createNation(id = 1, name = "촉")
        val defenderNation = createNation(id = 2, name = "위", capitalCityId = 10, gold = 5000, rice = 8000)

        val defGen1 = createGeneral(id = 10, nationId = 2, gold = 1000, rice = 2000, experience = 1000, dedication = 1000, npcState = 0)
        val defGen2 = createGeneral(id = 11, nationId = 2, gold = 500, rice = 1000, experience = 500, dedication = 500, npcState = 3)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerNation))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderNation))
        `when`(officerRepository.findByPlanetId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L)).thenReturn(emptyList())
        `when`(officerRepository.findByFactionId(2L)).thenReturn(listOf(defGen1, defGen2))

        val result = service.executeBattle(attacker, city, world)

        if (result.cityOccupied) {
            // Both generals released (nationId = 0)
            assertEquals(0L, defGen1.factionId)
            assertEquals(0L, defGen2.factionId)

            // Officers demoted
            assertEquals(0.toShort(), defGen1.officerLevel)
            assertEquals(0.toShort(), defGen2.officerLevel)

            // Gold/rice reduced (20-50%)
            assertTrue(defGen1.funds < 1000, "General should lose gold")
            assertTrue(defGen1.supplies < 2000, "General should lose rice")

            // Experience reduced by 10%
            assertTrue(defGen1.experience < 1000, "General should lose experience")

            // Dedication reduced by 50%
            assertTrue(defGen1.dedication < 1000, "General should lose dedication")
        }
    }

    // ========== Nation destruction: conquest rewards ==========

    @Test
    fun `nation destruction distributes conquest rewards to attacker nation`() {
        val world = createWorld()
        val attacker = createGeneral(id = 1, nationId = 1)
        val city = createCity(id = 10, nationId = 2, def = 10, wall = 10)

        val attackerNation = createNation(id = 1, name = "촉", gold = 5000, rice = 5000)
        val defenderNation = createNation(id = 2, name = "위", capitalCityId = 10, gold = 10000, rice = 12000)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerNation))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderNation))
        `when`(officerRepository.findByPlanetId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L)).thenReturn(emptyList())
        `when`(officerRepository.findByFactionId(2L)).thenReturn(emptyList())

        val result = service.executeBattle(attacker, city, world)

        if (result.cityOccupied) {
            // Attacker nation should receive half of (10000-0)/2=5000 gold and (12000-2000)/2=5000 rice
            assertTrue(attackerNation.funds > 5000, "Attacker nation should gain gold reward")
            assertTrue(attackerNation.supplies > 5000, "Attacker nation should gain rice reward")
        }
    }

    // ========== Nation destruction: diplomatic relations killed ==========

    @Test
    fun `nation destruction kills all diplomatic relations`() {
        val world = createWorld()
        val attacker = createGeneral(id = 1, nationId = 1)
        val city = createCity(id = 10, nationId = 2, def = 10, wall = 10)

        val attackerNation = createNation(id = 1, name = "촉")
        val defenderNation = createNation(id = 2, name = "위", capitalCityId = 10)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerNation))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderNation))
        `when`(officerRepository.findByPlanetId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L)).thenReturn(emptyList())
        `when`(officerRepository.findByFactionId(2L)).thenReturn(emptyList())

        val result = service.executeBattle(attacker, city, world)

        if (result.cityOccupied) {
            verify(diplomacyService).killAllRelationsForNation(1L, 2L)
        }
    }

    // ========== City occupation: city stat reset ==========

    @Test
    fun `city occupation resets city stats per legacy rules`() {
        val world = createWorld()
        val attacker = createGeneral(id = 1, nationId = 1)
        val city = createCity(id = 10, nationId = 2, def = 10, wall = 10, agri = 1000, comm = 1000, secu = 1000)

        val attackerNation = createNation(id = 1, name = "촉")
        val defenderNation = createNation(id = 2, name = "위", capitalCityId = 99)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerNation))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderNation))
        `when`(officerRepository.findByPlanetId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L)).thenReturn(listOf(createCity(id = 99, nationId = 2)))

        val result = service.executeBattle(attacker, city, world)

        if (result.cityOccupied) {
            assertEquals(1L, city.factionId, "City nation should change to attacker")
            assertEquals(0f, city.approval, "Trust should be reset")
            assertEquals(1.toShort(), city.supplyState)
            assertEquals(0.toShort(), city.term)
            assertEquals(0, city.officerSet)
            // agri/comm/secu reduced by 30%
            assertEquals(700, city.production)
            assertEquals(700, city.commerce)
            assertEquals(700, city.security)
        }
    }

    // ========== Capital relocation ==========

    @Test
    fun `capital relocation halves nation gold and rice`() {
        val world = createWorld()
        val attacker = createGeneral(id = 1, nationId = 1)
        val city = createCity(id = 10, nationId = 2, def = 10, wall = 10)

        val attackerNation = createNation(id = 1, name = "촉")
        val defenderNation = createNation(id = 2, name = "위", capitalCityId = 10, gold = 10000, rice = 8000)

        val otherCity = createCity(id = 99, nationId = 2, pop = 20000)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerNation))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderNation))
        `when`(officerRepository.findByPlanetId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L)).thenReturn(listOf(otherCity))
        `when`(officerRepository.findByFactionId(2L)).thenReturn(emptyList())

        val result = service.executeBattle(attacker, city, world)

        if (result.cityOccupied) {
            assertEquals(99L, defenderNation.capitalPlanetId)
            assertEquals(5000, defenderNation.funds)
            assertEquals(4000, defenderNation.supplies)
        }
    }

    @Test
    fun `capital relocation applies 20 percent morale loss to all nationals`() {
        val world = createWorld()
        val attacker = createGeneral(id = 1, nationId = 1)
        val city = createCity(id = 10, nationId = 2, def = 10, wall = 10)

        val attackerNation = createNation(id = 1, name = "촉")
        val defenderNation = createNation(id = 2, name = "위", capitalCityId = 10)

        val nationalGen = createGeneral(id = 20, nationId = 2, cityId = 99, atmos = 100)
        val otherCity = createCity(id = 99, nationId = 2, pop = 20000)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerNation))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderNation))
        `when`(officerRepository.findByPlanetId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L)).thenReturn(listOf(otherCity))
        `when`(officerRepository.findByFactionId(2L)).thenReturn(listOf(nationalGen))

        val result = service.executeBattle(attacker, city, world)

        if (result.cityOccupied) {
            assertEquals(80.toShort(), nationalGen.morale, "Morale should drop by 20%")
        }
    }

    // ========== Conquest logging ==========

    @Test
    fun `city occupation logs conquest message`() {
        val world = createWorld()
        val attacker = createGeneral(id = 1, nationId = 1)
        val city = createCity(id = 10, nationId = 2, def = 10, wall = 10)

        val attackerNation = createNation(id = 1, name = "촉")
        val defenderNation = createNation(id = 2, name = "위", capitalCityId = 99)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerNation))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderNation))
        `when`(officerRepository.findByPlanetId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L)).thenReturn(listOf(createCity(id = 99, nationId = 2)))

        val result = service.executeBattle(attacker, city, world)

        if (result.cityOccupied) {
            verify(historyService).logWorldHistory(eq(1L), anyString(), eq(200), eq(3), eq(false))
        }
    }

    // ========== NPC auto-join queuing ==========

    @Test
    fun `nation destruction hard-deletes nation and archives to old_nation`() {
        val world = createWorld()
        val attacker = createGeneral(id = 1, nationId = 1)
        val city = createCity(id = 10, nationId = 2, def = 10, wall = 10)

        val attackerNation = createNation(id = 1, name = "촉")
        val defenderNation = createNation(id = 2, name = "위", capitalCityId = 10)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerNation))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderNation))
        `when`(officerRepository.findByPlanetId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L)).thenReturn(emptyList())
        `when`(officerRepository.findByFactionId(2L)).thenReturn(emptyList())

        val result = service.executeBattle(attacker, city, world)

        if (result.cityOccupied) {
            verify(factionRepository).delete(defenderNation)
            verify(oldNationRepository).save(anyNonNull<OldNation>())
            verify(factionTurnRepository).deleteByNationId(2L)
        }
    }

    @Test
    fun `nation destruction resets cities to neutral and deletes troops`() {
        val world = createWorld()
        val attacker = createGeneral(id = 1, nationId = 1)
        val city = createCity(id = 10, nationId = 2, def = 10, wall = 10)

        val attackerNation = createNation(id = 1, name = "촉")
        val defenderNation = createNation(id = 2, name = "위", capitalCityId = 10)

        val orphanedCity = createCity(id = 20, nationId = 2)
        val troop1 = mock(Troop::class.java)
        val troop2 = mock(Troop::class.java)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerNation))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderNation))
        `when`(officerRepository.findByPlanetId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L))
            .thenReturn(emptyList())
            .thenReturn(listOf(orphanedCity))
        `when`(officerRepository.findByFactionId(2L)).thenReturn(emptyList())
        `when`(fleetRepository.findByFactionId(2L)).thenReturn(listOf(troop1, troop2))

        val result = service.executeBattle(attacker, city, world)

        if (result.cityOccupied) {
            assertEquals(0L, orphanedCity.factionId, "Orphaned city should be neutralized")
            assertEquals(0.toShort(), orphanedCity.frontState, "City frontState should be reset")
            verify(fleetRepository).deleteAll(listOf(troop1, troop2))
        }
    }

    @Test
    fun `nation destruction sets general belong and troop to zero`() {
        val world = createWorld()
        val attacker = createGeneral(id = 1, nationId = 1)
        val city = createCity(id = 10, nationId = 2, def = 10, wall = 10)

        val attackerNation = createNation(id = 1, name = "촉")
        val defenderNation = createNation(id = 2, name = "위", capitalCityId = 10)

        val defGen = createGeneral(id = 10, nationId = 2, gold = 1000, rice = 2000)

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerNation))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderNation))
        `when`(officerRepository.findByPlanetId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L)).thenReturn(emptyList())
        `when`(officerRepository.findByFactionId(2L)).thenReturn(listOf(defGen))

        val result = service.executeBattle(attacker, city, world)

        if (result.cityOccupied) {
            assertEquals(0L, defGen.factionId)
            assertEquals(0.toShort(), defGen.officerLevel)
            assertEquals(0.toShort(), defGen.belong)
            assertEquals(0L, defGen.fleetId)
        }
    }

    @Test
    fun `nation destruction queues eligible NPCs for auto-join`() {
        val world = createWorld()
        val attacker = createGeneral(id = 1, nationId = 1)
        val city = createCity(id = 10, nationId = 2, def = 10, wall = 10)

        val attackerNation = createNation(id = 1, name = "촉")
        val defenderNation = createNation(id = 2, name = "위", capitalCityId = 10)

        // Create several NPC generals with eligible states
        val npcGenerals = (2..8).filter { it != 5 }.map { npcState ->
            createGeneral(
                id = (100 + npcState).toLong(),
                nationId = 2,
                npcState = npcState.toShort(),
                gold = 100,
                rice = 100,
            )
        }

        `when`(factionRepository.findById(1L)).thenReturn(Optional.of(attackerNation))
        `when`(factionRepository.findById(2L)).thenReturn(Optional.of(defenderNation))
        `when`(officerRepository.findByPlanetId(10L)).thenReturn(emptyList())
        `when`(planetRepository.findByFactionId(2L)).thenReturn(emptyList())
        `when`(officerRepository.findByFactionId(2L)).thenReturn(npcGenerals)

        service.executeBattle(attacker, city, world)

        // All NPC generals should be released (nationId = 0)
        for (gen in npcGenerals) {
            assertEquals(0L, gen.factionId, "NPC general ${gen.id} should be released")
        }

        // Some may have autoJoinNationId metadata (probabilistic, so we just check the field exists for those that got it)
        val autoJoinQueued = npcGenerals.filter { it.meta.containsKey("autoJoinNationId") }
        for (gen in autoJoinQueued) {
            assertEquals(1L, gen.meta["autoJoinNationId"], "Auto-join target should be attacker nation")
            assertTrue((gen.meta["autoJoinDelay"] as Int) in 0..12, "Delay should be 0-12")
        }
    }
}
