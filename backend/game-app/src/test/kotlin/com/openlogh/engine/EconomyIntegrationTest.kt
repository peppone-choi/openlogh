package com.openlogh.engine

import com.openlogh.entity.Faction
import com.openlogh.entity.Fleet
import com.openlogh.entity.FleetWarehouse
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import com.openlogh.entity.PlanetWarehouse
import com.openlogh.entity.SessionState
import com.openlogh.entity.Event
import com.openlogh.repository.EventRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.FleetWarehouseRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.PlanetWarehouseRepository
import com.openlogh.repository.SessionStateRepository
import com.openlogh.service.FezzanEndingService
import com.openlogh.service.FezzanEndingResult
import com.openlogh.service.FezzanService
import com.openlogh.service.GameEventService
import com.openlogh.service.ShipyardProductionService
import com.openlogh.service.TransferRequest
import com.openlogh.service.WarehouseService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

/**
 * Phase 4 경제 시스템 통합 테스트.
 *
 * 6개 ECON 요구사항(ECON-01 ~ ECON-06)이 각 서비스 계약대로 동작하는지 검증한다.
 * Spring 컨텍스트 없이 단위 mock 방식으로 동작.
 */
class EconomyIntegrationTest {

    // --- repositories (mocked) ---
    private lateinit var factionRepository: FactionRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var planetWarehouseRepository: PlanetWarehouseRepository
    private lateinit var fleetWarehouseRepository: FleetWarehouseRepository
    private lateinit var fleetRepository: FleetRepository
    private lateinit var officerRepository: OfficerRepository
    private lateinit var eventRepository: EventRepository
    private lateinit var sessionStateRepository: SessionStateRepository
    private lateinit var fezzanService: FezzanService
    private lateinit var gameEventService: GameEventService

    // --- services under test ---
    private lateinit var gin7EconomyService: Gin7EconomyService
    private lateinit var shipyardProductionService: ShipyardProductionService
    private lateinit var warehouseService: WarehouseService
    private lateinit var fezzanEndingService: FezzanEndingService
    private lateinit var fleetSortieCostService: FleetSortieCostService

    @BeforeEach
    fun setUp() {
        factionRepository = mock(FactionRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        planetWarehouseRepository = mock(PlanetWarehouseRepository::class.java)
        fleetWarehouseRepository = mock(FleetWarehouseRepository::class.java)
        fleetRepository = mock(FleetRepository::class.java)
        officerRepository = mock(OfficerRepository::class.java)
        eventRepository = mock(EventRepository::class.java)
        sessionStateRepository = mock(SessionStateRepository::class.java)
        fezzanService = mock(FezzanService::class.java)
        gameEventService = mock(GameEventService::class.java)

        gin7EconomyService = Gin7EconomyService(factionRepository, planetRepository)
        shipyardProductionService = ShipyardProductionService(planetWarehouseRepository, planetRepository)
        warehouseService = WarehouseService(planetWarehouseRepository, fleetWarehouseRepository, fleetRepository)
        fezzanEndingService = FezzanEndingService(
            factionRepository, eventRepository, fezzanService, gameEventService, sessionStateRepository
        )
        fleetSortieCostService = FleetSortieCostService(fleetRepository, officerRepository, factionRepository)
    }

    // ─────────────── helpers ───────────────

    private fun makeWorld(month: Int = 2): SessionState {
        val w = SessionState()
        w.id = 1.toShort()
        w.currentMonth = month.toShort()
        w.currentYear = 800.toShort()
        return w
    }

    private fun makeFaction(
        id: Long,
        sessionId: Long = 1L,
        taxRate: Int = 30,
        funds: Int = 0,
        factionType: String = "empire",
    ): Faction {
        val f = Faction()
        f.id = id
        f.sessionId = sessionId
        f.taxRate = taxRate.toShort()
        f.funds = funds
        f.factionType = factionType
        f.name = "Test Faction $id"
        return f
    }

    private fun makePlanet(
        id: Long,
        sessionId: Long = 1L,
        factionId: Long = 1L,
        population: Int = 10000,
        populationMax: Int = 20000,
        production: Int = 400,
        productionMax: Int = 1000,
        commerce: Int = 10000,
        commerceMax: Int = 20000,
        supplyState: Short = 1,
    ): Planet {
        val p = Planet(id = id, name = "Planet $id", factionId = factionId, production = production)
        p.sessionId = sessionId
        p.population = population
        p.populationMax = populationMax
        p.productionMax = productionMax
        p.commerce = commerce
        p.commerceMax = commerceMax
        p.supplyState = supplyState
        return p
    }

    private fun makePlanetWarehouse(
        id: Long = 1L,
        sessionId: Long = 1L,
        planetId: Long = 10L,
        hasShipyard: Boolean = true,
        battleship: Int = 0,
    ): PlanetWarehouse {
        return PlanetWarehouse(
            id = id,
            sessionId = sessionId,
            planetId = planetId,
            hasShipyard = hasShipyard,
            battleship = battleship,
        )
    }

    private fun makeFleetWarehouse(
        id: Long = 2L,
        sessionId: Long = 1L,
        fleetId: Long = 20L,
        battleship: Int = 0,
    ): FleetWarehouse {
        return FleetWarehouse(
            id = id,
            sessionId = sessionId,
            fleetId = fleetId,
            battleship = battleship,
        )
    }

    private fun makeFleet(
        id: Long,
        sessionId: Long = 1L,
        factionId: Long = 1L,
        currentUnits: Int = 10,
        commanderId: Long = 100L,
        isSortie: Boolean = true,
    ): Fleet {
        val f = Fleet()
        f.id = id
        f.sessionId = sessionId
        f.factionId = factionId
        f.currentUnits = currentUnits
        f.leaderOfficerId = commanderId
        if (isSortie) f.meta["isSortie"] = true as Any
        return f
    }

    private fun makeOfficer(id: Long, administration: Short = 50): Officer {
        val o = Officer()
        o.id = id
        o.administration = administration
        return o
    }

    // ─────────────── ECON-01: 행성 자원 성장 ───────────────

    /**
     * ECON-01: Gin7EconomyService.processMonthly() → planet.population 0.5% 증가
     */
    @Test
    fun `ECON-01 processMonthly increases planet population each month`() {
        // ECON-01: 행성 자원 성장 — supplyState=1 행성의 population이 0.5% 성장한다
        val world = makeWorld(month = 2) // non-tax month
        val faction = makeFaction(id = 1L, sessionId = 1L, taxRate = 30)
        val planet = makePlanet(
            id = 100L, factionId = 1L,
            population = 10000, populationMax = 20000,
        )

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))

        gin7EconomyService.processMonthly(world)

        // 10000 * 1.005 = 10049.999... → toInt() = 10049 (floating point truncation)
        // The service uses (population * 1.005).toInt() which truncates, so >= 10049 is correct
        assertTrue(
            planet.population >= 10049,
            "ECON-01: population should grow by at least 0.5%: expected >= 10049 but was ${planet.population}"
        )
        verify(planetRepository).saveAll(anyList())
    }

    // ─────────────── ECON-02: 조병창 자동생산 ───────────────

    /**
     * ECON-02: ShipyardProductionService.runProduction() → PlanetWarehouse.battleship 증가
     */
    @Test
    fun `ECON-02 runProduction adds ships to PlanetWarehouse`() {
        // ECON-02: 조병창 자동생산 — production=400인 행성에서 battleship 2유닛 생산 (400/200=2)
        val sessionId = 10L
        val planet = makePlanet(id = 1L, sessionId = sessionId, factionId = 1L, production = 400)
        val pw = makePlanetWarehouse(planetId = planet.id, sessionId = sessionId, hasShipyard = true)

        `when`(planetWarehouseRepository.findBySessionIdAndHasShipyardTrue(sessionId)).thenReturn(listOf(pw))
        `when`(planetRepository.findById(planet.id)).thenReturn(Optional.of(planet))
        `when`(planetWarehouseRepository.save(any(PlanetWarehouse::class.java))).thenAnswer { it.arguments[0] }

        val reports = shipyardProductionService.runProduction(sessionId)

        // ECON-02: production=400 → baseOutput = 400/200 = 2 유닛
        assertEquals(1, reports.size, "ECON-02: exactly 1 production report expected")
        assertEquals(2, reports[0].shipsProduced, "ECON-02: production=400 should yield 2 ships (400/200)")
        verify(planetWarehouseRepository).save(any(PlanetWarehouse::class.java))
    }

    // ─────────────── ECON-03: 세율 납입 ───────────────

    /**
     * ECON-03: Gin7EconomyService.processMonthly() month=1 → faction.funds 증가
     */
    @Test
    fun `ECON-03 processMonthly on tax month adds funds to faction`() {
        // ECON-03: 세율 납입 — month=1(세금달), commerce=10000, taxRate=30 → 3000 자금 징수
        val world = makeWorld(month = 1) // tax month
        val faction = makeFaction(id = 1L, sessionId = 1L, taxRate = 30, funds = 0)
        val planet = makePlanet(
            id = 100L, factionId = 1L,
            commerce = 10000, supplyState = 1,
        )

        `when`(factionRepository.findBySessionId(1L)).thenReturn(listOf(faction))
        `when`(planetRepository.findBySessionId(1L)).thenReturn(listOf(planet))

        gin7EconomyService.processMonthly(world)

        // taxRevenue = 10000 * 30 / 100 = 3000
        assertEquals(3000, faction.funds, "ECON-03: tax revenue should be 3000 (10000 * 30%)")
        verify(factionRepository).saveAll(anyList())
    }

    // ─────────────── ECON-04: 창고 이동 ───────────────

    /**
     * ECON-04: WarehouseService.transferToFleet() → planetWarehouse 감소, fleetWarehouse 증가
     */
    @Test
    fun `ECON-04 transferToFleet moves ships from planet to fleet warehouse`() {
        // ECON-04: 창고 이동 — planetWarehouse(battleship=5) → fleetWarehouse(battleship=3) 이동
        val sessionId = 1L
        val planetId = 10L
        val fleetId = 20L

        val pw = makePlanetWarehouse(planetId = planetId, sessionId = sessionId, battleship = 5)
        val fw = makeFleetWarehouse(fleetId = fleetId, sessionId = sessionId, battleship = 0)

        `when`(planetWarehouseRepository.findBySessionIdAndPlanetId(sessionId, planetId)).thenReturn(pw)
        `when`(fleetWarehouseRepository.findBySessionIdAndFleetId(sessionId, fleetId)).thenReturn(fw)
        `when`(planetWarehouseRepository.save(any(PlanetWarehouse::class.java))).thenAnswer { it.arguments[0] }
        `when`(fleetWarehouseRepository.save(any(FleetWarehouse::class.java))).thenAnswer { it.arguments[0] }

        val result = warehouseService.transferToFleet(
            sessionId = sessionId,
            planetId = planetId,
            fleetId = fleetId,
            request = TransferRequest(ships = mapOf("BATTLESHIP" to 3)),
        )

        // ECON-04: planetWarehouse.battleship=5-3=2, fleetWarehouse.battleship=0+3=3
        assertTrue(result.success, "ECON-04: transfer should succeed")
        assertEquals(2, pw.battleship, "ECON-04: planet warehouse should have 2 battleships remaining")
        assertEquals(3, fw.battleship, "ECON-04: fleet warehouse should have 3 battleships")
        assertTrue(
            result.transferred.any { it.contains("BATTLESHIP") && it.contains("3") },
            "ECON-04: transferred list should mention BATTLESHIP 3유닛"
        )
    }

    // ─────────────── ECON-05: 페잔 차관 엔딩 ───────────────

    /**
     * ECON-05: FezzanEndingService.checkAndTrigger() with 3 defaulted loans → ending triggered
     */
    @Test
    fun `ECON-05 checkAndTrigger fires ending when 3 loans defaulted`() {
        // ECON-05: 페잔 차관 엔딩 — 연체 3건 시 triggerFezzanEnding 호출, meta 플래그 설정
        val sessionId = 1L
        val factionId = 10L

        val world = makeWorld()
        world.id = sessionId.toShort()
        // fezzanEndingTriggered not set (fresh world)

        val faction = makeFaction(id = factionId, sessionId = sessionId)

        `when`(sessionStateRepository.findById(sessionId.toShort())).thenReturn(Optional.of(world))
        `when`(fezzanService.checkFezzanEnding(sessionId))
            .thenReturn(FezzanEndingResult(triggered = true, dominatedFactionId = factionId))
        `when`(factionRepository.findById(factionId)).thenReturn(Optional.of(faction))
        `when`(eventRepository.save(any(Event::class.java))).thenAnswer { it.arguments[0] }

        fezzanEndingService.checkAndTrigger(sessionId)

        // ECON-05: triggerFezzanEnding called → eventRepository.save invoked
        verify(eventRepository).save(any(Event::class.java))
        // ECON-05: fezzanEndingTriggered flag set to true in world.meta
        assertEquals(true, world.meta["fezzanEndingTriggered"],
            "ECON-05: fezzanEndingTriggered should be set in world.meta")
        verify(sessionStateRepository).save(world)
    }

    // ─────────────── ECON-06: 함대 출격비용 ───────────────

    /**
     * ECON-06: FleetSortieCostService.processSortieCost() → faction.funds 감소, 마이너스 없음
     */
    @Test
    fun `ECON-06 processSortieCost deducts funds proportional to active fleets`() {
        // ECON-06: 함대 출격비용 — 출격함대 1개(10유닛), administration=50, 비용 차감 확인
        val sessionId = 1L
        val factionId = 1L
        val initialFunds = 10000
        val faction = makeFaction(id = factionId, sessionId = sessionId, funds = initialFunds)

        val fleet = makeFleet(
            id = 1L, sessionId = sessionId, factionId = factionId,
            currentUnits = 10, commanderId = 100L, isSortie = true,
        )
        val commander = makeOfficer(id = 100L, administration = 50)

        `when`(fleetRepository.findBySessionId(sessionId)).thenReturn(listOf(fleet))
        `when`(factionRepository.findById(factionId)).thenReturn(Optional.of(faction))
        `when`(officerRepository.findById(100L)).thenReturn(Optional.of(commander))
        `when`(factionRepository.saveAll(anyList())).thenAnswer { it.arguments[0] }

        fleetSortieCostService.processSortieCost(sessionId)

        // ECON-06: funds should have decreased (cost = 10 * BASE_COST_PER_UNIT = 100)
        assertTrue(faction.funds < initialFunds,
            "ECON-06: faction.funds should decrease after sortie cost: was $initialFunds, now ${faction.funds}")
        // ECON-06: funds must not go negative
        assertTrue(faction.funds >= 0,
            "ECON-06: faction.funds must not be negative: was ${faction.funds}")
        // Verify expected deduction: baseCost=100, admin=50 → discount=0, finalCost=100
        assertEquals(initialFunds - 100, faction.funds,
            "ECON-06: expected funds = ${initialFunds - 100} (10 units * 10 base cost)")
    }
}
