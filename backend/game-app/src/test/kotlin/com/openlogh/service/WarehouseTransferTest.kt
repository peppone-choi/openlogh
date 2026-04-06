package com.openlogh.service

import com.openlogh.entity.FleetWarehouse
import com.openlogh.entity.PlanetWarehouse
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.FleetWarehouseRepository
import com.openlogh.repository.PlanetWarehouseRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class WarehouseTransferTest {

    private lateinit var planetWarehouseRepository: PlanetWarehouseRepository
    private lateinit var fleetWarehouseRepository: FleetWarehouseRepository
    private lateinit var fleetRepository: FleetRepository
    private lateinit var warehouseService: WarehouseService

    private lateinit var planetWarehouse: PlanetWarehouse
    private lateinit var fleetWarehouse: FleetWarehouse

    @BeforeEach
    fun setUp() {
        planetWarehouseRepository = mock(PlanetWarehouseRepository::class.java)
        fleetWarehouseRepository = mock(FleetWarehouseRepository::class.java)
        fleetRepository = mock(FleetRepository::class.java)

        warehouseService = WarehouseService(
            planetWarehouseRepository,
            fleetWarehouseRepository,
            fleetRepository,
        )

        planetWarehouse = PlanetWarehouse(
            id = 1L,
            sessionId = 1L,
            planetId = 10L,
            battleship = 5,
            cruiser = 3,
            supplies = 1000,
            missiles = 200,
        )

        fleetWarehouse = FleetWarehouse(
            id = 2L,
            sessionId = 1L,
            fleetId = 20L,
            battleship = 0,
            cruiser = 2,
            supplies = 100,
        )

        `when`(planetWarehouseRepository.findBySessionIdAndPlanetId(1L, 10L)).thenReturn(planetWarehouse)
        `when`(fleetWarehouseRepository.findBySessionIdAndFleetId(1L, 20L)).thenReturn(fleetWarehouse)
        `when`(planetWarehouseRepository.save(any(PlanetWarehouse::class.java))).thenAnswer { it.arguments[0] as PlanetWarehouse }
        `when`(fleetWarehouseRepository.save(any(FleetWarehouse::class.java))).thenAnswer { it.arguments[0] as FleetWarehouse }
    }

    // Test 1: transferToFleet ships=3 BATTLESHIP
    @Test
    fun `transferToFleet ships BATTLESHIP 3 decreases planet and increases fleet`() {
        val result = warehouseService.transferToFleet(
            sessionId = 1L,
            planetId = 10L,
            fleetId = 20L,
            request = TransferRequest(ships = mapOf("BATTLESHIP" to 3)),
        )

        assertTrue(result.success)
        assertEquals(2, planetWarehouse.battleship)   // 5 - 3
        assertEquals(3, fleetWarehouse.battleship)    // 0 + 3
    }

    // Test 2: transferToFleet 보유량(2) 초과 요청(5) → 실제 이동량 2
    @Test
    fun `transferToFleet exceeding available amount transfers only available ships`() {
        planetWarehouse.cruiser = 2

        val result = warehouseService.transferToFleet(
            sessionId = 1L,
            planetId = 10L,
            fleetId = 20L,
            request = TransferRequest(ships = mapOf("CRUISER" to 5)),
        )

        assertTrue(result.success)
        assertEquals(0, planetWarehouse.cruiser)     // 2 - 2
        assertEquals(4, fleetWarehouse.cruiser)      // 2 + 2
        assertTrue(result.transferred.any { s -> s.contains("2") })
    }

    // Test 3: transferToFleet supplies=500
    @Test
    fun `transferToFleet supplies 500 decreases planet and increases fleet supplies`() {
        val result = warehouseService.transferToFleet(
            sessionId = 1L,
            planetId = 10L,
            fleetId = 20L,
            request = TransferRequest(supplies = 500),
        )

        assertTrue(result.success)
        assertEquals(500, planetWarehouse.supplies)   // 1000 - 500
        assertEquals(600, fleetWarehouse.supplies)    // 100 + 500
    }

    // Test 4: returnToPlanet ships=2 CRUISER
    @Test
    fun `returnToPlanet ships CRUISER 2 decreases fleet and increases planet`() {
        val result = warehouseService.returnToPlanet(
            sessionId = 1L,
            fleetId = 20L,
            planetId = 10L,
            request = TransferRequest(ships = mapOf("CRUISER" to 2)),
        )

        assertTrue(result.success)
        assertEquals(0, fleetWarehouse.cruiser)    // 2 - 2
        assertEquals(5, planetWarehouse.cruiser)   // 3 + 2
    }

    // Test 5: returnToPlanet supplies=100
    @Test
    fun `returnToPlanet supplies 100 decreases fleet and increases planet supplies`() {
        val result = warehouseService.returnToPlanet(
            sessionId = 1L,
            fleetId = 20L,
            planetId = 10L,
            request = TransferRequest(supplies = 100),
        )

        assertTrue(result.success)
        assertEquals(0, fleetWarehouse.supplies)      // 100 - 100
        assertEquals(1100, planetWarehouse.supplies)  // 1000 + 100
    }

    // Test 6: TransferResult contains transferred items list
    @Test
    fun `transferToFleet result transferred list contains actual moved items`() {
        val result = warehouseService.transferToFleet(
            sessionId = 1L,
            planetId = 10L,
            fleetId = 20L,
            request = TransferRequest(
                ships = mapOf("BATTLESHIP" to 2),
                supplies = 300,
            ),
        )

        assertTrue(result.success)
        assertTrue(result.transferred.isNotEmpty())
        assertTrue(result.transferred.any { s -> s.contains("BATTLESHIP") || s.contains("2") })
        assertTrue(result.transferred.any { s -> s.contains("물자") || s.contains("300") })
    }
}
