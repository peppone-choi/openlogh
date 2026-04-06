package com.openlogh.service

import com.openlogh.entity.Planet
import com.openlogh.entity.PlanetWarehouse
import com.openlogh.model.ShipClassType
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.PlanetWarehouseRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.Optional

/**
 * Unit tests for ShipyardProductionService.
 *
 * Verifies gin7 production rules:
 * - baseOutput = production / 200, coerceAtLeast(1)
 * - supplyOutput = production / 100, coerceAtLeast(1)
 * - Neutral planets (factionId=0) skipped
 * - planet.meta["shipyardClass"] configures produced ship class
 * - ProductionReport includes factionId from planet
 */
class ShipyardProductionServiceTest {

    private lateinit var planetWarehouseRepository: PlanetWarehouseRepository
    private lateinit var planetRepository: PlanetRepository
    private lateinit var service: ShipyardProductionService

    @BeforeEach
    fun setUp() {
        planetWarehouseRepository = mock(PlanetWarehouseRepository::class.java)
        planetRepository = mock(PlanetRepository::class.java)
        service = ShipyardProductionService(planetWarehouseRepository, planetRepository)
    }

    // ---------- helpers ----------

    private fun makePlanet(
        id: Long = 1L,
        production: Int = 200,
        factionId: Long = 1L,
        meta: MutableMap<String, Any> = mutableMapOf(),
    ): Planet {
        val p = Planet(id = id, name = "Test Planet", factionId = factionId, production = production)
        p.meta = meta
        return p
    }

    private fun makeWarehouse(planetId: Long = 1L, sessionId: Long = 10L) = PlanetWarehouse(
        id = 1L,
        sessionId = sessionId,
        planetId = planetId,
        hasShipyard = true,
    )

    private fun stubSave() {
        `when`(planetWarehouseRepository.save(any(PlanetWarehouse::class.java)))
            .thenAnswer { it.arguments[0] }
    }

    // ---------- Test 1: production=200 → baseOutput=1 ----------

    @Test
    fun `test1 production 200 yields baseOutput 1`() {
        val planet = makePlanet(production = 200, factionId = 1L)
        val pw = makeWarehouse(planetId = planet.id)

        `when`(planetWarehouseRepository.findBySessionIdAndHasShipyardTrue(10L))
            .thenReturn(listOf(pw))
        `when`(planetRepository.findById(planet.id)).thenReturn(Optional.of(planet))
        stubSave()

        val reports = service.runProduction(10L)

        assertEquals(1, reports.size)
        assertEquals(1, reports[0].shipsProduced)
    }

    // ---------- Test 2: production=1000 → baseOutput=5 ----------

    @Test
    fun `test2 production 1000 yields baseOutput 5`() {
        val planet = makePlanet(production = 1000, factionId = 1L)
        val pw = makeWarehouse(planetId = planet.id)

        `when`(planetWarehouseRepository.findBySessionIdAndHasShipyardTrue(10L))
            .thenReturn(listOf(pw))
        `when`(planetRepository.findById(planet.id)).thenReturn(Optional.of(planet))
        stubSave()

        val reports = service.runProduction(10L)

        assertEquals(1, reports.size)
        assertEquals(5, reports[0].shipsProduced)
    }

    // ---------- Test 3: production=0 → baseOutput=1 (minimum 1) ----------

    @Test
    fun `test3 production 0 yields baseOutput 1 minimum`() {
        val planet = makePlanet(production = 0, factionId = 1L)
        val pw = makeWarehouse(planetId = planet.id)

        `when`(planetWarehouseRepository.findBySessionIdAndHasShipyardTrue(10L))
            .thenReturn(listOf(pw))
        `when`(planetRepository.findById(planet.id)).thenReturn(Optional.of(planet))
        stubSave()

        val reports = service.runProduction(10L)

        assertEquals(1, reports.size)
        assertEquals(1, reports[0].shipsProduced)
    }

    // ---------- Test 4: hasShipyard=false planet not included ----------

    @Test
    fun `test4 planet without shipyard is excluded from production`() {
        // findBySessionIdAndHasShipyardTrue returns no warehouses (repository filters this)
        `when`(planetWarehouseRepository.findBySessionIdAndHasShipyardTrue(10L))
            .thenReturn(emptyList())

        val reports = service.runProduction(10L)

        assertTrue(reports.isEmpty())
        verify(planetRepository, never()).findById(anyLong())
    }

    // ---------- Test 5: factionId=0 (neutral) → not produced ----------

    @Test
    fun `test5 neutral planet factionId 0 is skipped and not in reports`() {
        val planet = makePlanet(production = 500, factionId = 0L)
        val pw = makeWarehouse(planetId = planet.id)

        `when`(planetWarehouseRepository.findBySessionIdAndHasShipyardTrue(10L))
            .thenReturn(listOf(pw))
        `when`(planetRepository.findById(planet.id)).thenReturn(Optional.of(planet))

        val reports = service.runProduction(10L)

        assertTrue(reports.isEmpty(), "Neutral planet should not appear in production reports")
        verify(planetWarehouseRepository, never()).save(any(PlanetWarehouse::class.java))
    }

    // ---------- Test 6: planet.meta["shipyardClass"] drives ship class ----------

    @Test
    fun `test6 shipyardClass CRUISER in meta produces CRUISER ships`() {
        val planet = makePlanet(
            production = 200,
            factionId = 1L,
            meta = mutableMapOf("shipyardClass" to "CRUISER")
        )
        val pw = makeWarehouse(planetId = planet.id)

        `when`(planetWarehouseRepository.findBySessionIdAndHasShipyardTrue(10L))
            .thenReturn(listOf(pw))
        `when`(planetRepository.findById(planet.id)).thenReturn(Optional.of(planet))
        stubSave()

        val reports = service.runProduction(10L)

        assertEquals(1, reports.size)
        assertEquals(ShipClassType.CRUISER, reports[0].shipClass)
    }

    @Test
    fun `test6b no shipyardClass meta defaults to BATTLESHIP`() {
        val planet = makePlanet(production = 200, factionId = 1L)
        val pw = makeWarehouse(planetId = planet.id)

        `when`(planetWarehouseRepository.findBySessionIdAndHasShipyardTrue(10L))
            .thenReturn(listOf(pw))
        `when`(planetRepository.findById(planet.id)).thenReturn(Optional.of(planet))
        stubSave()

        val reports = service.runProduction(10L)

        assertEquals(1, reports.size)
        assertEquals(ShipClassType.BATTLESHIP, reports[0].shipClass)
    }

    // ---------- Test 7: supplyOutput = production / 100, coerceAtLeast(1) ----------

    @Test
    fun `test7 supplyOutput is production divided by 100 minimum 1`() {
        val planet = makePlanet(production = 1000, factionId = 1L)
        val pw = makeWarehouse(planetId = planet.id)

        `when`(planetWarehouseRepository.findBySessionIdAndHasShipyardTrue(10L))
            .thenReturn(listOf(pw))
        `when`(planetRepository.findById(planet.id)).thenReturn(Optional.of(planet))
        stubSave()

        val reports = service.runProduction(10L)

        assertEquals(1, reports.size)
        assertEquals(10, reports[0].suppliesProduced) // 1000 / 100 = 10
    }

    // ---------- factionId included in report ----------

    @Test
    fun `report includes factionId from planet`() {
        val planet = makePlanet(production = 200, factionId = 42L)
        val pw = makeWarehouse(planetId = planet.id)

        `when`(planetWarehouseRepository.findBySessionIdAndHasShipyardTrue(10L))
            .thenReturn(listOf(pw))
        `when`(planetRepository.findById(planet.id)).thenReturn(Optional.of(planet))
        stubSave()

        val reports = service.runProduction(10L)

        assertEquals(1, reports.size)
        assertEquals(42L, reports[0].factionId)
    }
}
