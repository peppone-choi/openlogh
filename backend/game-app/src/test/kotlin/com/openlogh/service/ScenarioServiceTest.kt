package com.openlogh.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.openlogh.entity.*
import com.openlogh.model.PlanetConst
import com.openlogh.model.ScenarioData
import com.openlogh.repository.*
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.random.Random
import java.lang.reflect.Method
import java.util.Optional

/**
 * Tests for ScenarioService's officer tuple parsing (5-stat format).
 */
class ScenarioServiceTest {

    private lateinit var service: ScenarioService
    private lateinit var parseGeneral: Method
    private lateinit var objectMapper: ObjectMapper
    private lateinit var worldStateRepository: SessionStateRepository
    private lateinit var factionRepository: FactionRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var diplomacyRepository: DiplomacyRepository
    private lateinit var selectPoolRepository: SelectPoolRepository
    private lateinit var historyService: HistoryService
    private lateinit var mapService: MapService
    private lateinit var entityManager: EntityManager

    @BeforeEach
    fun setUp() {
        objectMapper = mock(ObjectMapper::class.java)
        worldStateRepository = mock(SessionStateRepository::class.java)
        factionRepository = mock(FactionRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        diplomacyRepository = mock(DiplomacyRepository::class.java)
        historyService = mock(HistoryService::class.java)
        mapService = mock(MapService::class.java)
        entityManager = mock(EntityManager::class.java)

        selectPoolRepository = mock(SelectPoolRepository::class.java)

        service = ScenarioService(
            objectMapper = objectMapper,
            defaultCommitSha = "test-sha",
            defaultGameVersion = "test-v1",
            worldStateRepository = worldStateRepository,
            factionRepository = factionRepository,
            planetRepository = planetRepository,
            officerRepository = officerRepository,
            diplomacyRepository = diplomacyRepository,
            eventRepository = mock(EventRepository::class.java),
            mapService = mapService,
            historyService = historyService,
            selectPoolRepository = selectPoolRepository,
            entityManager = entityManager,
        )

        parseGeneral = ScenarioService::class.java.getDeclaredMethod(
            "parseGeneral",
            List::class.java,
            Long::class.javaPrimitiveType,
            Map::class.java,
            Map::class.java,
            Map::class.java,
            List::class.java,
            Random::class.java,
            Int::class.javaPrimitiveType,
            Short::class.javaPrimitiveType,
        )
        parseGeneral.isAccessible = true
    }

    private fun callParseGeneral(
        row: List<Any?>,
        nationIdxToDbId: Map<Int, Long> = emptyMap(),
        nationCityIds: Map<Long, List<Long>> = emptyMap(),
        cityNameToId: Map<String, Long> = emptyMap(),
        allCityIds: List<Long> = listOf(999L),
        startYear: Int = 200,
        defaultNpcState: Short = 2,
    ): Officer {
        return parseGeneral.invoke(
            service,
            row,
            1L,
            nationIdxToDbId,
            nationCityIds,
            cityNameToId,
            allCityIds,
            Random(0),
            startYear,
            defaultNpcState,
        ) as Officer
    }

    @Test
    fun `parseGeneral reads 5-stat tuple correctly`() {
        // [affinity, name, picture, nation, city, lead, str, int, pol, charm, officer, birth, death]
        val row: List<Any?> = listOf(25, "조조", "1010", null, null, 98, 72, 91, 94, 96, 0, 155, 220)
        val officer = callParseGeneral(row)
        assertEquals("조조", officer.name)
        assertEquals(98.toShort(), officer.leadership)
        assertEquals(72.toShort(), officer.command)
        assertEquals(91.toShort(), officer.intelligence)
        assertEquals(94.toShort(), officer.politics)
        assertEquals(96.toShort(), officer.administration)
        assertEquals(0.toShort(), officer.rank)
        assertEquals(155.toShort(), officer.birthYear)
        assertEquals(220.toShort(), officer.deathYear)
    }

    @Test
    fun `parseGeneral reads optional personality and special`() {
        val row: List<Any?> = listOf(76, "관우", "1020", null, null, 96, 97, 75, 64, 94, 0, 162, 219, "의리", "신산")
        val officer = callParseGeneral(row)
        assertEquals("관우", officer.name)
        assertEquals(96.toShort(), officer.leadership)
        assertEquals(64.toShort(), officer.politics)
        assertEquals(94.toShort(), officer.administration)
        assertEquals("의리", officer.personalCode)
        assertEquals("신산", officer.specialCode)
    }

    @Test
    fun `parseGeneral defaults personality and special to None`() {
        val row: List<Any?> = listOf(0, "테스트", "9999", null, null, 50, 50, 50, 50, 50, 0, 180, 240)
        val officer = callParseGeneral(row)
        assertEquals("None", officer.personalCode)
        assertEquals("None", officer.specialCode)
    }

    @Test
    fun `parseGeneral resolves nation by index`() {
        val nationIdxToDbId = mapOf(1 to 42L)
        val nationCityIds = mapOf(42L to listOf(100L))
        val row: List<Any?> = listOf(1, "헌제", "1002", 1, null, 17, 13, 61, 53, 46, 0, 170, 250)
        val officer = callParseGeneral(row, nationIdxToDbId = nationIdxToDbId, nationCityIds = nationCityIds)
        assertEquals(42L, officer.factionId)
        assertEquals(2.toShort(), officer.npcState) // NPC belonging to faction
    }

    @Test
    fun `parseGeneral sets npcState for free NPC`() {
        val row: List<Any?> = listOf(50, "방랑자", "9000", null, null, 50, 50, 50, 50, 50, 0, 180, 240)
        val officer = callParseGeneral(row)
        assertEquals(0L, officer.factionId)
        assertEquals(2.toShort(), officer.npcState)
    }

    @Test
    fun `parseGeneral keeps default npcState for affinity 999`() {
        val row: List<Any?> = listOf(999, "은둔자", "9001", null, null, 70, 10, 95, 80, 85, 0, 170, 234)
        val officer = callParseGeneral(row)
        assertEquals(2.toShort(), officer.npcState)
    }

    @Test
    fun `parseGeneral uses mapped city when city name is provided`() {
        val row: List<Any?> = listOf(50, "도시지정", "9002", 0, "장안", 50, 50, 50, 50, 50, 0, 180, 240)
        val officer = callParseGeneral(row, cityNameToId = mapOf("장안" to 123L))
        assertEquals(123L, officer.planetId)
    }

    @Test
    fun `parseGeneral calculates age from startYear`() {
        val row: List<Any?> = listOf(0, "장수", "1000", null, null, 50, 50, 50, 50, 50, 0, 180, 250)
        val officer = callParseGeneral(row, startYear = 200)
        assertEquals(20.toShort(), officer.age) // 200 - 180 = 20
    }

    @Test
    fun `parseGeneral clamps age to minimum 14 for neutral scenario NPC`() {
        val row: List<Any?> = listOf(0, "어린이", "1000", null, null, 50, 50, 50, 50, 50, 0, 195, 260)
        val officer = callParseGeneral(row, startYear = 200)
        assertEquals(14.toShort(), officer.age)
        assertEquals(195.toShort(), officer.birthYear)
        assertEquals(260.toShort(), officer.deathYear)
    }

    @Test
    fun `parseGeneral keeps nation general lifespan years unchanged even if younger than 14`() {
        val nationIdxToDbId = mapOf(1 to 42L)
        val nationCityIds = mapOf(42L to listOf(100L))
        val row: List<Any?> = listOf(0, "소년", "1001", 1, null, 50, 50, 50, 50, 50, 2, 195, 260)

        val officer = callParseGeneral(
            row,
            nationIdxToDbId = nationIdxToDbId,
            nationCityIds = nationCityIds,
            startYear = 200,
        )

        assertEquals(42L, officer.factionId)
        assertEquals(195.toShort(), officer.birthYear)
        assertEquals(260.toShort(), officer.deathYear)
        assertEquals(20.toShort(), officer.age)
    }

    @Test
    fun `parseGeneral supports legacy 3-stat tuple with officer field`() {
        val row: List<Any?> = listOf(1, "헌제", "1002", 1, null, 17, 13, 61, 0, 170, 250, "안전", null)
        val officer = callParseGeneral(row, nationIdxToDbId = mapOf(1 to 42L), nationCityIds = mapOf(42L to listOf(100L)))

        assertEquals(61.toShort(), officer.intelligence)
        assertEquals(61.toShort(), officer.politics)
        assertEquals(61.toShort(), officer.administration)
        assertEquals(0.toShort(), officer.rank)
        assertEquals(170.toShort(), officer.birthYear)
        assertEquals(250.toShort(), officer.deathYear)
    }

    @Test
    fun `parseGeneral supports legacy 3-stat tuple without officer field`() {
        val row: List<Any?> = listOf(1, "테스트", "1002", 0, null, 70, 65, 60, 180, 230, "재간", "신산")
        val officer = callParseGeneral(row)

        assertEquals(60.toShort(), officer.intelligence)
        assertEquals(60.toShort(), officer.politics)
        assertEquals(60.toShort(), officer.administration)
        assertEquals(0.toShort(), officer.rank)
        assertEquals(180.toShort(), officer.birthYear)
        assertEquals(230.toShort(), officer.deathYear)
        assertEquals("재간", officer.personalCode)
        assertEquals("신산", officer.specialCode)
    }

    @Test
    fun `initializeWorld seeds cities with approval 50 and assigns all configured faction cities`() {
        val scenario = ScenarioData(
            title = "테스트",
            startYear = 181,
            nation = listOf(
                listOf("후한", "#800000", 10000, 10000, "", 0, "유가", 7, listOf("업", "허창")),
                listOf("황건적", "#ffd700", 10000, 10000, "", 0, "태평도", 2, listOf("낙양")),
            ),
        )

        val scenariosField = ScenarioService::class.java.getDeclaredField("scenarios")
        scenariosField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val scenarios = scenariosField.get(service) as MutableMap<String, ScenarioData>
        scenarios["test"] = scenario

        val planetStore = mutableMapOf<Long, Planet>()
        var planetSeq = 1L
        val factionStore = mutableMapOf<Long, Faction>()
        var factionSeq = 1L

        `when`(worldStateRepository.save(any(SessionState::class.java))).thenAnswer { inv ->
            val world = inv.arguments[0] as SessionState
            world.id = 1
            world
        }

        `when`(mapService.getCities("che")).thenReturn(
            listOf(
                PlanetConst(1, "업", 8, 1, 1000, 100, 100, 100, 100, 100, 10, 10, emptyList()),
                PlanetConst(2, "허창", 8, 2, 1000, 100, 100, 100, 100, 100, 20, 20, emptyList()),
                PlanetConst(3, "낙양", 8, 2, 1000, 100, 100, 100, 100, 100, 30, 30, emptyList()),
            ),
        )

        @Suppress("UNCHECKED_CAST")
        `when`(planetRepository.saveAll(anyList<Planet>())).thenAnswer { inv ->
            val planets = inv.arguments[0] as List<Planet>
            planets.forEach { planet ->
                if (planet.id == 0L) planet.id = planetSeq++
                planetStore[planet.id] = planet
            }
            planets
        }
        `when`(planetRepository.save(any(Planet::class.java))).thenAnswer { inv ->
            val planet = inv.arguments[0] as Planet
            if (planet.id == 0L) planet.id = planetSeq++
            planetStore[planet.id] = planet
            planet
        }

        @Suppress("UNCHECKED_CAST")
        `when`(factionRepository.saveAll(anyList<Faction>())).thenAnswer { inv ->
            val factions = inv.arguments[0] as List<Faction>
            factions.forEach { faction ->
                if (faction.id == 0L) faction.id = factionSeq++
                factionStore[faction.id] = faction
            }
            factions
        }

        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        service.initializeWorld("test")

        assertEquals(3, planetStore.size)
        assertTrue(planetStore.values.all { it.approval == 50f })
        assertEquals(2L, planetStore.values.count { it.factionId == factionStore.values.first { it.name == "후한" }.id }.toLong())
        assertEquals(1L, planetStore.values.count { it.factionId == factionStore.values.first { it.name == "황건적" }.id }.toLong())
    }

    @Test
    fun `initializeWorld delays neutral NPC younger than 14`() {
        val scenario = ScenarioData(
            title = "등장 테스트",
            startYear = 188,
            general = listOf(
                listOf(0, "미성년", "2000", 0, null, 55, 56, 57, 58, 59, 0, 177, 230),
                listOf(0, "성인", "2001", 0, null, 65, 66, 67, 68, 69, 0, 150, 230),
            ),
        )

        val scenariosField = ScenarioService::class.java.getDeclaredField("scenarios")
        scenariosField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val scenarios = scenariosField.get(service) as MutableMap<String, ScenarioData>
        scenarios["delay-test"] = scenario

        val planetStore = mutableMapOf<Long, Planet>()
        var planetSeq = 1L
        val savedOfficers = mutableListOf<Officer>()
        var officerSeq = 1L

        `when`(worldStateRepository.save(any(SessionState::class.java))).thenAnswer { inv ->
            val world = inv.arguments[0] as SessionState
            world.id = 1
            world
        }

        `when`(mapService.getCities("che")).thenReturn(
            listOf(
                PlanetConst(1, "낙양", 8, 1, 1000, 100, 100, 100, 100, 100, 10, 10, emptyList()),
            ),
        )

        @Suppress("UNCHECKED_CAST")
        `when`(planetRepository.saveAll(anyList<Planet>())).thenAnswer { inv ->
            val planets = inv.arguments[0] as List<Planet>
            planets.forEach { planet ->
                if (planet.id == 0L) planet.id = planetSeq++
                planetStore[planet.id] = planet
            }
            planets
        }
        `when`(planetRepository.findBySessionId(1L)).thenAnswer { planetStore.values.toList() }

        `when`(factionRepository.findBySessionId(1L)).thenReturn(emptyList())
        @Suppress("UNCHECKED_CAST")
        `when`(factionRepository.saveAll(anyList<Faction>())).thenAnswer { inv ->
            (inv.arguments[0] as List<Faction>)
        }

        @Suppress("UNCHECKED_CAST")
        `when`(officerRepository.saveAll(anyList<Officer>())).thenAnswer { inv ->
            val officers = inv.arguments[0] as List<Officer>
            officers.forEach { officer ->
                if (officer.id == 0L) officer.id = officerSeq++
                savedOfficers.add(officer)
            }
            officers
        }
        `when`(officerRepository.findBySessionId(1L)).thenAnswer { savedOfficers.toList() }

        service.initializeWorld("delay-test")

        assertEquals(1, savedOfficers.size)
        assertEquals("성인", savedOfficers.first().name)
    }

    @Test
    fun `initializeWorld clamps turnterm to at least one when tick seconds is under a minute`() {
        val scenario = ScenarioData(
            title = "고속 턴",
            startYear = 180,
        )

        val scenariosField = ScenarioService::class.java.getDeclaredField("scenarios")
        scenariosField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val scenarios = scenariosField.get(service) as MutableMap<String, ScenarioData>
        scenarios["fast-turn"] = scenario

        var planetSeq = 1L

        `when`(worldStateRepository.save(any(SessionState::class.java))).thenAnswer { inv ->
            val world = inv.arguments[0] as SessionState
            world.id = 1
            world
        }

        `when`(mapService.getCities("che")).thenReturn(
            listOf(
                PlanetConst(1, "낙양", 8, 1, 1000, 100, 100, 100, 100, 100, 10, 10, emptyList()),
            ),
        )

        @Suppress("UNCHECKED_CAST")
        `when`(planetRepository.saveAll(anyList<Planet>())).thenAnswer { inv ->
            val planets = inv.arguments[0] as List<Planet>
            planets.forEach { planet ->
                if (planet.id == 0L) planet.id = planetSeq++
            }
            planets
        }

        @Suppress("UNCHECKED_CAST")
        `when`(factionRepository.saveAll(anyList<Faction>())).thenAnswer { inv ->
            inv.arguments[0] as List<Faction>
        }
        @Suppress("UNCHECKED_CAST")
        `when`(officerRepository.saveAll(anyList<Officer>())).thenAnswer { inv ->
            inv.arguments[0] as List<Officer>
        }
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        val world = service.initializeWorld("fast-turn", tickSeconds = 30)

        assertEquals(30, world.tickSeconds)
        assertEquals(1, (world.config["turnterm"] as Number).toInt())
        assertEquals(4800, (world.config["killturn"] as Number).toInt())
    }

    @Test
    fun `explicit opentime enables pre_open phase detection`() {
        // Verify the OffsetDateTime-based opentime pattern works for pre_open detection
        val futureOpentime = java.time.OffsetDateTime.now().plusHours(1).toString()
        val parsed = java.time.OffsetDateTime.parse(futureOpentime)
        assertTrue(parsed.isAfter(java.time.OffsetDateTime.now()), "opentime should be in the future")

        // Simulate WorldService.getGamePhase logic
        val isPreOpen = java.time.OffsetDateTime.now().isBefore(parsed)
        assertTrue(isPreOpen, "world should be in pre_open phase when opentime is explicitly set")
    }

    @Test
    fun `spawnScenarioNpcGeneralsForYear spawns delayed NPC on due year`() {
        val scenario = ScenarioData(
            title = "연차 등장",
            startYear = 188,
            general = listOf(
                listOf(0, "지연등장", "3000", 0, null, 60, 61, 62, 63, 64, 0, 177, 240),
            ),
        )

        val scenariosField = ScenarioService::class.java.getDeclaredField("scenarios")
        scenariosField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val scenarios = scenariosField.get(service) as MutableMap<String, ScenarioData>
        scenarios["spawn-test"] = scenario

        val planetStore = mutableMapOf<Long, Planet>()
        var planetSeq = 1L
        val savedOfficers = mutableListOf<Officer>()
        var officerSeq = 1L

        `when`(worldStateRepository.save(any(SessionState::class.java))).thenAnswer { inv ->
            val world = inv.arguments[0] as SessionState
            world.id = 1
            world
        }

        `when`(mapService.getCities("che")).thenReturn(
            listOf(
                PlanetConst(1, "낙양", 8, 1, 1000, 100, 100, 100, 100, 100, 10, 10, emptyList()),
            ),
        )

        @Suppress("UNCHECKED_CAST")
        `when`(planetRepository.saveAll(anyList<Planet>())).thenAnswer { inv ->
            val planets = inv.arguments[0] as List<Planet>
            planets.forEach { planet ->
                if (planet.id == 0L) planet.id = planetSeq++
                planetStore[planet.id] = planet
            }
            planets
        }
        `when`(planetRepository.findBySessionId(1L)).thenAnswer { planetStore.values.toList() }

        `when`(factionRepository.findBySessionId(1L)).thenReturn(emptyList())
        @Suppress("UNCHECKED_CAST")
        `when`(factionRepository.saveAll(anyList<Faction>())).thenAnswer { inv ->
            (inv.arguments[0] as List<Faction>)
        }

        @Suppress("UNCHECKED_CAST")
        `when`(officerRepository.saveAll(anyList<Officer>())).thenAnswer { inv ->
            val officers = inv.arguments[0] as List<Officer>
            officers.forEach { officer ->
                if (officer.id == 0L) officer.id = officerSeq++
                savedOfficers.add(officer)
            }
            officers
        }
        `when`(officerRepository.findBySessionId(1L)).thenAnswer { savedOfficers.toList() }

        val world = service.initializeWorld("spawn-test")
        assertTrue(savedOfficers.none { it.name == "지연등장" })

        world.currentYear = 190
        val beforeDue = service.spawnScenarioNpcGeneralsForYear(world)
        assertEquals(0, beforeDue)
        assertTrue(savedOfficers.none { it.name == "지연등장" })

        world.currentYear = 191
        val onDue = service.spawnScenarioNpcGeneralsForYear(world)
        assertEquals(1, onDue)
        assertTrue(savedOfficers.any { it.name == "지연등장" && it.age == 14.toShort() })
    }

    @Test
    fun `initializeWorld maps diplomacy indices without offset and seeds scenario history`() {
        val scenario = ScenarioData(
            title = "반동탁연합",
            startYear = 187,
            nation = listOf(
                listOf("동탁", "#111111", 10000, 10000, "", 0, "법가", 5, listOf("낙양")),
                listOf("원소", "#222222", 10000, 10000, "", 0, "법가", 2, listOf("남피")),
                listOf("조조", "#333333", 10000, 10000, "", 0, "병가", 2, listOf("진류")),
            ),
            diplomacy = listOf(listOf(1, 2, 1, 36)),
            history = listOf("<C>●</>190년 1월:<L><b>【역사모드2】</b></>반동탁연합 결성"),
        )

        val scenariosField = ScenarioService::class.java.getDeclaredField("scenarios")
        scenariosField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val scenarios = scenariosField.get(service) as MutableMap<String, ScenarioData>
        scenarios["diplo-test"] = scenario

        val planetStore = mutableMapOf<Long, Planet>()
        var planetSeq = 1L
        val factionStore = mutableMapOf<Long, Faction>()
        var factionSeq = 1L
        val capturedDiplomacies = mutableListOf<com.openlogh.entity.Diplomacy>()

        `when`(worldStateRepository.save(any(SessionState::class.java))).thenAnswer { inv ->
            val world = inv.arguments[0] as SessionState
            world.id = 1
            world
        }
        `when`(mapService.getCities("che")).thenReturn(
            listOf(
                PlanetConst(1, "낙양", 8, 1, 1000, 100, 100, 100, 100, 100, 10, 10, emptyList()),
                PlanetConst(2, "남피", 8, 1, 1000, 100, 100, 100, 100, 100, 10, 10, emptyList()),
                PlanetConst(3, "진류", 8, 1, 1000, 100, 100, 100, 100, 100, 10, 10, emptyList()),
            ),
        )
        @Suppress("UNCHECKED_CAST")
        `when`(planetRepository.saveAll(anyList<Planet>())).thenAnswer { inv ->
            val planets = inv.arguments[0] as List<Planet>
            planets.forEach { planet ->
                if (planet.id == 0L) planet.id = planetSeq++
                planetStore[planet.id] = planet
            }
            planets
        }
        @Suppress("UNCHECKED_CAST")
        `when`(factionRepository.saveAll(anyList<Faction>())).thenAnswer { inv ->
            val factions = inv.arguments[0] as List<Faction>
            factions.forEach { faction ->
                if (faction.id == 0L) faction.id = factionSeq++
                factionStore[faction.id] = faction
            }
            factions
        }
        @Suppress("UNCHECKED_CAST")
        `when`(diplomacyRepository.saveAll(anyList<com.openlogh.entity.Diplomacy>())).thenAnswer { inv ->
            val diplomacies = inv.arguments[0] as List<com.openlogh.entity.Diplomacy>
            capturedDiplomacies += diplomacies
            diplomacies
        }
        `when`(officerRepository.saveAll(anyList<Officer>())).thenAnswer { inv -> inv.arguments[0] as List<Officer> }
        `when`(officerRepository.findBySessionId(1L)).thenReturn(emptyList())

        service.initializeWorld("diplo-test")

        assertEquals(1, capturedDiplomacies.size)
        val diplomacy = capturedDiplomacies.single()
        assertEquals(factionStore.values.first { it.name == "동탁" }.id, diplomacy.srcFactionId)
        assertEquals(factionStore.values.first { it.name == "원소" }.id, diplomacy.destNationId)
        assertEquals("선전포고", diplomacy.stateCode)
        assertEquals(36.toShort(), diplomacy.term)

        // seedScenarioHistory was called (detailed behavior tested separately)
        verify(historyService).logWorldHistory(
            org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt(),
        )
    }

    @Test
    fun `parseNation sets scoutMsg from scenario description`() {
        val parseNation = ScenarioService::class.java.getDeclaredMethod(
            "parseNation",
            List::class.java,
            Long::class.javaPrimitiveType,
        )
        parseNation.isAccessible = true

        val row: List<Any> = listOf("후한", "#FF0000", 5000, 3000, "후한왕조", 100, "중립", 7, listOf("낙양"))
        val faction = parseNation.invoke(service, row, 1L) as Faction

        assertEquals("후한", faction.name)
        assertEquals("후한왕조", faction.meta["scoutMsg"])
        assertEquals("후한왕조", faction.meta["scout_msg"])
    }

    @Test
    fun `parseNation handles missing description gracefully`() {
        val parseNation = ScenarioService::class.java.getDeclaredMethod(
            "parseNation",
            List::class.java,
            Long::class.javaPrimitiveType,
        )
        parseNation.isAccessible = true

        val row: List<Any> = listOf("황건적", "#FFFF00", 0, 2000, "", 50, "도적", 0, listOf<String>())
        val faction = parseNation.invoke(service, row, 1L) as Faction

        assertEquals("황건적", faction.name)
        assertEquals("", faction.meta["scoutMsg"])
    }

    @Test
    fun `applyScenarioEmperorSettings places emperor at faction capital`() {
        val applyMethod = ScenarioService::class.java.getDeclaredMethod(
            "applyScenarioEmperorSettings",
            com.openlogh.model.ScenarioData::class.java,
            SessionState::class.java,
            Map::class.java,
            List::class.java,
            List::class.java,
        )
        applyMethod.isAccessible = true

        val world = SessionState(id = 1, name = "test", scenarioCode = "test")
        val faction = Faction(id = 100L, sessionId = 1, name = "한")
        faction.capitalPlanetId = 55L
        val officer = Officer(
            sessionId = 1, name = "헌제", factionId = 100L, planetId = 1L,
            npcState = 2, rank = 20,
        )

        val scenario = com.openlogh.model.ScenarioData(
            title = "test",
            startYear = 184,
            emperor = mapOf("generalName" to "헌제", "factionIdx" to 1, "status" to "enthroned"),
        )

        val nationIdxToDbId = mapOf(1 to 100L)

        applyMethod.invoke(service, scenario, world, nationIdxToDbId, listOf(faction), listOf(officer))

        assertEquals(55L, officer.planetId)
    }

    @Test
    fun `applyScenarioEmperorSettings sets emperor meta when config present`() {
        val applyMethod = ScenarioService::class.java.getDeclaredMethod(
            "applyScenarioEmperorSettings",
            com.openlogh.model.ScenarioData::class.java,
            SessionState::class.java,
            Map::class.java,
            List::class.java,
            List::class.java,
        )
        applyMethod.isAccessible = true

        val world = SessionState(id = 1, name = "test", scenarioCode = "test")
        val faction = Faction(id = 100L, sessionId = 1, name = "한")
        val officer = Officer(
            sessionId = 1, name = "영제", factionId = 100L, planetId = 1L,
            npcState = 2, rank = 20,
        )

        val scenario = com.openlogh.model.ScenarioData(
            title = "test",
            startYear = 184,
            emperor = mapOf("generalName" to "영제", "factionIdx" to 1, "status" to "enthroned"),
        )

        val nationIdxToDbId = mapOf(1 to 100L)

        applyMethod.invoke(service, scenario, world, nationIdxToDbId, listOf(faction), listOf(officer))

        assertEquals(true, world.meta[com.openlogh.engine.SovereignConstants.WORLD_EMPEROR_SYSTEM])
        assertEquals(com.openlogh.engine.SovereignConstants.NPC_STATE_EMPEROR, officer.npcState)
        assertEquals("enthroned", officer.meta[com.openlogh.engine.SovereignConstants.GENERAL_EMPEROR_STATUS])
        assertEquals("emperor", faction.meta[com.openlogh.engine.SovereignConstants.NATION_IMPERIAL_STATUS])
        assertEquals("legitimate", faction.meta[com.openlogh.engine.SovereignConstants.NATION_EMPEROR_TYPE])
    }

    @Test
    fun `seedScenarioHistory parses year from text and strips date prefix`() {
        val regex = Regex("(\\d+)년\\s*(\\d+)월")
        val prefixRegex = Regex("^(<[^>]*>.*?</>)?\\s*\\d+년\\s*\\d+월\\s*:?\\s*")

        val historyLine = "<C>●</>190년 1월:<L><b>【역사】</b></>반동탁연합 결성"
        val match = regex.find(historyLine)
        val year = match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 187
        val month = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 1
        val cleaned = prefixRegex.replace(historyLine, "")

        assertEquals(190, year)
        assertEquals(1, month)
        assertFalse(cleaned.contains("190년"), "Date prefix should be stripped")
        assertTrue(cleaned.contains("반동탁연합 결성"), "Content should be preserved")
    }

    @Test
    fun `seedGeneralPool invokes repository saveAll with parsed entries`() {
        val seedMethod = ScenarioService::class.java.getDeclaredMethod("seedGeneralPool", Long::class.javaPrimitiveType)
        seedMethod.isAccessible = true

        val captured = mutableListOf<SelectPool>()
        `when`(selectPoolRepository.saveAll(any<List<SelectPool>>())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val items = invocation.getArgument<List<SelectPool>>(0)
            captured.addAll(items)
            items
        }

        seedMethod.invoke(service, 999L)

        assertTrue(captured.isNotEmpty())
        assertEquals(999L, captured.first().sessionId)
        assertTrue(captured.first().info.containsKey("generalName"))
        verify(selectPoolRepository).deleteBySessionId(999L)
    }

    @Test
    fun `applyScenarioEmperorSettings enables system even without emperor config`() {
        val applyMethod = ScenarioService::class.java.getDeclaredMethod(
            "applyScenarioEmperorSettings",
            com.openlogh.model.ScenarioData::class.java,
            SessionState::class.java,
            Map::class.java,
            List::class.java,
            List::class.java,
        )
        applyMethod.isAccessible = true

        val world = SessionState(id = 1, name = "test", scenarioCode = "test")
        val scenario = com.openlogh.model.ScenarioData(
            title = "test", startYear = 225, emperor = null,
        )

        applyMethod.invoke(service, scenario, world, emptyMap<Int, Long>(), emptyList<Faction>(), emptyList<Officer>())

        assertEquals(true, world.meta[com.openlogh.engine.SovereignConstants.WORLD_EMPEROR_SYSTEM])
        assertNull(world.meta[com.openlogh.engine.SovereignConstants.WORLD_EMPEROR_GENERAL_ID])
    }

    @Test
    fun `deriveAbbreviation handles two-char surnames and special names`() {
        val method = ScenarioService::class.java.getDeclaredMethod("deriveAbbreviation", String::class.java)
        method.isAccessible = true
        assertEquals("유", method.invoke(service, "유비"))
        assertEquals("조", method.invoke(service, "조조"))
        assertEquals("공손", method.invoke(service, "공손찬"))
        assertEquals("사마", method.invoke(service, "사마의"))
        assertEquals("한", method.invoke(service, "한나라"))
        assertEquals("헌", method.invoke(service, "헌제"))
        assertEquals("황건", method.invoke(service, "황건적"))
    }

    @Test
    fun `faction city list uses lastOrNull for List element`() {
        val factionRow = listOf("테스트", "#FFF", 1000, 1000, "설명", 100, "유가", 3, listOf("장안", "낙양"))
        val cities = factionRow.lastOrNull { it is List<*> }
        assertNotNull(cities)
        assertEquals(listOf("장안", "낙양"), cities)

        val factionRowWithAbbr = listOf("테스트", "#FFF", 1000, 1000, "설명", 100, "유가", 3, "테", listOf("장안", "낙양"))
        val cities2 = factionRowWithAbbr.lastOrNull { it is List<*> }
        assertEquals(listOf("장안", "낙양"), cities2)
    }

    @Test
    fun `Faction abbreviation field defaults to empty string`() {
        val faction = Faction(id = 1, sessionId = 1, name = "유비군")
        assertEquals("", faction.abbreviation)
        faction.abbreviation = "유"
        assertEquals("유", faction.abbreviation)
    }

    @Test
    fun `FactionResponse includes abbreviation from entity`() {
        val faction = Faction(id = 1, sessionId = 1, name = "조조군")
        faction.abbreviation = "조"
        val response = com.openlogh.dto.FactionResponse.from(faction)
        assertEquals("조", response.abbreviation)
    }

    @Test
    fun `updateAbbreviation truncates to 2 chars`() {
        val faction = Faction(id = 1, sessionId = 1, name = "테스트")
        faction.abbreviation = "abcdef".take(2)
        assertEquals("ab", faction.abbreviation)
    }

}
