package com.openlogh.engine.war

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WarFormulaTest {

    @Test
    fun `getTechLevel returns 0 for tech below 1000`() {
        assertEquals(0, getTechLevel(0f))
        assertEquals(0, getTechLevel(500f))
        assertEquals(0, getTechLevel(999f))
    }

    @Test
    fun `getTechLevel returns level based on thousands`() {
        assertEquals(1, getTechLevel(1000f))
        assertEquals(5, getTechLevel(5000f))
        assertEquals(10, getTechLevel(10000f))
        assertEquals(12, getTechLevel(15000f), "Should clamp to max 12")
    }

    @Test
    fun `getTechAbil returns level times 25`() {
        assertEquals(0, getTechAbil(0f))
        assertEquals(25, getTechAbil(1000f))
        assertEquals(250, getTechAbil(10000f))
    }

    @Test
    fun `getTechCost returns 1 plus level times 0_15`() {
        assertEquals(1.0, getTechCost(0f), 0.001)
        assertEquals(1.15, getTechCost(1000f), 0.001)
        assertEquals(2.5, getTechCost(10000f), 0.001)
    }

    @Test
    fun `getDexLevel returns 0 for dex below first threshold`() {
        assertEquals(0, getDexLevel(0))
        assertEquals(0, getDexLevel(349))
    }

    @Test
    fun `getDexLevel returns correct level for known thresholds`() {
        assertEquals(1, getDexLevel(350))
        assertEquals(1, getDexLevel(1374))
        assertEquals(2, getDexLevel(1375))
        assertEquals(3, getDexLevel(3500))
    }

    @Test
    fun `getDexLog returns 1_0 when both dex levels are equal`() {
        val result = getDexLog(0, 0)
        assertEquals(1.0, result, 0.001)

        val result2 = getDexLog(350, 1000)
        // Both are level 1
        assertEquals(1.0, result2, 0.001)
    }

    @Test
    fun `getDexLog returns value above 1 when dex1 level is higher`() {
        // dex1 level=2 (1375), dex2 level=0 (0) => diff = 2, result = 2/55 + 1 = ~1.036
        val result = getDexLog(1375, 0)
        assertTrue(result > 1.0, "getDexLog should be > 1.0 when dex1 > dex2")
        assertEquals(1.0 + 2.0 / 55.0, result, 0.001)
    }

    @Test
    fun `getDexLog returns value below 1 when dex2 level is higher`() {
        val result = getDexLog(0, 1375)
        assertTrue(result < 1.0, "getDexLog should be < 1.0 when dex2 > dex1")
        assertEquals(1.0 - 2.0 / 55.0, result, 0.001)
    }
}
