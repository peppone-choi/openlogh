package com.openlogh.engine.ai.strategic

import com.openlogh.entity.Fleet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FleetAllocatorTest {

    private fun createFleet(id: Long, currentUnits: Int): Fleet = Fleet(
        id = id,
        sessionId = 1,
        leaderOfficerId = 1,
        factionId = 1,
        name = "F$id",
        unitType = "FLEET",
        maxUnits = 60,
        currentUnits = currentUnits,
        planetId = 1,
    )

    @Test
    fun `allocates minimum fleets to exceed 1_3x enemy power`() {
        // Required enemy power = 100 → target = 130 (× SUPERIORITY_MARGIN 1.3)
        // Fleets sorted descending: 60, 50, 40, 30, 20
        // 60 < 130, 60+50=110 < 130, 60+50+40=150 ≥ 130 → stop after 3 fleets
        val fleets = listOf(
            createFleet(1, 60),
            createFleet(2, 50),
            createFleet(3, 40),
            createFleet(4, 30),
            createFleet(5, 20),
        )

        val result = FleetAllocator.allocateFleets(fleets, requiredEnemyPower = 100.0)

        assertEquals(listOf(1L, 2L, 3L), result.selectedFleetIds)
        assertEquals(150.0, result.allocatedPower)
    }

    @Test
    fun `returns empty when no fleets available`() {
        val result = FleetAllocator.allocateFleets(emptyList(), requiredEnemyPower = 100.0)
        assertTrue(result.selectedFleetIds.isEmpty())
        assertEquals(0.0, result.allocatedPower)
    }

    @Test
    fun `does not allocate all fleets when sufficient power reached early`() {
        // Required = 10 → target = 13
        // First fleet (50 units) already exceeds target → only 1 selected, 4 remain as defense reserve
        val fleets = listOf(
            createFleet(1, 50),
            createFleet(2, 30),
            createFleet(3, 20),
            createFleet(4, 10),
            createFleet(5, 5),
        )

        val result = FleetAllocator.allocateFleets(fleets, requiredEnemyPower = 10.0)

        assertEquals(1, result.selectedFleetIds.size)
        assertEquals(50.0, result.allocatedPower)
        // 4 remain as defense reserve (D-09)
        assertFalse(result.selectedFleetIds.containsAll(listOf(2L, 3L, 4L, 5L)))
    }

    @Test
    fun `allocates all fleets when even total power is insufficient`() {
        // Required = 1000 → target = 1300
        // Total available = 60 + 50 + 40 = 150, well below target
        val fleets = listOf(
            createFleet(1, 60),
            createFleet(2, 50),
            createFleet(3, 40),
        )

        val result = FleetAllocator.allocateFleets(fleets, requiredEnemyPower = 1000.0)
        assertEquals(3, result.selectedFleetIds.size)
        assertEquals(150.0, result.allocatedPower)
    }

    @Test
    fun `superiority margin constant is exactly 1_3`() {
        assertEquals(1.3, FleetAllocator.SUPERIORITY_MARGIN)
    }
}
