package com.opensam.entity

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class SelectPoolTest {

    @Test
    fun `SelectPool entity initializes with defaults`() {
        val pool = SelectPool(worldId = 1L, uniqueName = "test-pool")
        assertEquals(1L, pool.worldId)
        assertEquals("test-pool", pool.uniqueName)
        assertNull(pool.ownerId)
        assertNull(pool.generalId)
        assertNull(pool.reservedUntil)
        assertTrue(pool.info.isEmpty())
    }

    @Test
    fun `SelectPool info stores general template data`() {
        val pool = SelectPool(
            worldId = 1L,
            uniqueName = "pool-1",
            info = mutableMapOf(
                "generalName" to "조조",
                "leadership" to 96,
                "strength" to 72,
                "intel" to 91,
                "politics" to 93,
                "charm" to 96,
            ),
        )
        assertEquals("조조", pool.info["generalName"])
        assertEquals(96, pool.info["leadership"])
    }

    @Test
    fun `SelectPool reservation tracks owner and expiry`() {
        val pool = SelectPool(worldId = 1L, uniqueName = "pool-1")
        val expiry = OffsetDateTime.now().plusMinutes(10)
        pool.ownerId = 42L
        pool.reservedUntil = expiry

        assertEquals(42L, pool.ownerId)
        assertNotNull(pool.reservedUntil)
    }
}
