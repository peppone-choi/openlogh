package com.openlogh.engine.map

import com.openlogh.entity.Officer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MapServiceTest {

    // --- TerrainType speed values ---

    @Test
    fun `PLAIN has correct road and off-road speed`() {
        assertEquals(1.0f, TerrainType.PLAIN.roadSpeed)
        assertEquals(0.6f, TerrainType.PLAIN.offRoadSpeed)
    }

    @Test
    fun `FOREST has correct road and off-road speed`() {
        assertEquals(0.8f, TerrainType.FOREST.roadSpeed)
        assertEquals(0.4f, TerrainType.FOREST.offRoadSpeed)
    }

    @Test
    fun `MOUNTAIN has correct road and off-road speed`() {
        assertEquals(0.5f, TerrainType.MOUNTAIN.roadSpeed)
        assertEquals(0.2f, TerrainType.MOUNTAIN.offRoadSpeed)
    }

    @Test
    fun `WATER has correct road and off-road speed`() {
        assertEquals(0.8f, TerrainType.WATER.roadSpeed)
        assertEquals(0.0f, TerrainType.WATER.offRoadSpeed)
    }

    @Test
    fun `DESERT has correct road and off-road speed`() {
        assertEquals(0.9f, TerrainType.DESERT.roadSpeed)
        assertEquals(0.5f, TerrainType.DESERT.offRoadSpeed)
    }

    // --- Stub MapDataService for unit tests ---

    private fun stubMapDataService(speed: Float): MapDataService {
        return object : MapDataService() {
            override fun getMovementSpeed(mapCode: String, x: Float, y: Float) = speed
        }
    }

    private fun makeGeneral(posX: Float, posY: Float, destX: Float?, destY: Float?): Officer {
        return Officer().apply {
            this.posX = posX
            this.posY = posY
            this.destX = destX
            this.destY = destY
        }
    }

    // --- MovementService basic calculation ---

    @Test
    fun `no destination returns current position with arrived false`() {
        val service = MovementService(stubMapDataService(1.0f))
        val general = makeGeneral(10f, 20f, null, null)
        val (x, y, arrived) = service.calculateNextPosition(general, "che")
        assertEquals(10f, x)
        assertEquals(20f, y)
        assertFalse(arrived)
    }

    @Test
    fun `general within arrival threshold returns destination with arrived true`() {
        val service = MovementService(stubMapDataService(1.0f))
        val general = makeGeneral(10f, 10f, 10.5f, 10.5f)
        val (x, y, arrived) = service.calculateNextPosition(general, "che")
        assertEquals(10.5f, x)
        assertEquals(10.5f, y)
        assertTrue(arrived)
    }

    @Test
    fun `general moves toward destination at full speed on PLAIN road`() {
        val service = MovementService(stubMapDataService(1.0f))
        // dest is far away: 100 units due east
        val general = makeGeneral(0f, 0f, 100f, 0f)
        val (newX, newY, arrived) = service.calculateNextPosition(general, "che")
        // step = BASE_SPEED * 1.0 = 15f
        assertEquals(15f, newX, 0.001f)
        assertEquals(0f, newY, 0.001f)
        assertFalse(arrived)
    }

    @Test
    fun `general moves at reduced speed off-road on MOUNTAIN`() {
        // MOUNTAIN off-road = 0.2f
        val service = MovementService(stubMapDataService(0.2f))
        val general = makeGeneral(0f, 0f, 100f, 0f)
        val (newX, newY, arrived) = service.calculateNextPosition(general, "che")
        // step = 15 * 0.2 = 3f
        assertEquals(3f, newX, 0.001f)
        assertEquals(0f, newY, 0.001f)
        assertFalse(arrived)
    }

    @Test
    fun `general arrives when step exceeds remaining distance`() {
        val service = MovementService(stubMapDataService(1.0f))
        // destination is only 5 units away, step = 15 — should arrive
        val general = makeGeneral(0f, 0f, 5f, 0f)
        val (x, y, arrived) = service.calculateNextPosition(general, "che")
        assertEquals(5f, x)
        assertEquals(0f, y)
        assertTrue(arrived)
    }
}
