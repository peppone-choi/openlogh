package com.openlogh.engine.tactical

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for command breakdown detection (SUCC-06):
 * When all commanders are incapacitated, every surviving unit transitions to OutOfCrcBehavior.
 */
class CommandBreakdownTest {

    private fun makeHierarchy(
        fleetCommander: Long,
        successionQueue: List<Long> = emptyList(),
        designatedSuccessor: Long? = null,
    ) = CommandHierarchy(
        fleetCommander = fleetCommander,
        successionQueue = successionQueue.toMutableList(),
        designatedSuccessor = designatedSuccessor,
    )

    private fun makeUnit(
        fleetId: Long = 1L,
        officerId: Long = 1L,
        side: BattleSide = BattleSide.ATTACKER,
        hp: Int = 100,
        maxHp: Int = 100,
        isAlive: Boolean = true,
        isRetreating: Boolean = false,
    ): TacticalUnit = TacticalUnit(
        fleetId = fleetId,
        officerId = officerId,
        officerName = "Officer$officerId",
        factionId = 1L,
        side = side,
        hp = hp,
        maxHp = maxHp,
        isAlive = isAlive,
        isRetreating = isRetreating,
    )

    // ── isCommandBroken tests ──

    @Test
    fun `isCommandBroken returns false when active commander alive`() {
        val h = makeHierarchy(fleetCommander = 1L, successionQueue = listOf(2L))
        assertFalse(SuccessionService.isCommandBroken(h, setOf(1L, 2L)))
    }

    @Test
    fun `isCommandBroken returns false when successor available`() {
        val h = makeHierarchy(fleetCommander = 1L, successionQueue = listOf(2L, 3L))
        // Commander 1 is dead, but 2 and 3 are alive
        assertFalse(SuccessionService.isCommandBroken(h, setOf(2L, 3L)))
    }

    @Test
    fun `isCommandBroken returns false when designated successor alive`() {
        val h = makeHierarchy(fleetCommander = 1L, designatedSuccessor = 5L)
        // Commander 1 is dead, but designated successor 5 is alive
        assertFalse(SuccessionService.isCommandBroken(h, setOf(5L)))
    }

    @Test
    fun `isCommandBroken returns true when all candidates dead`() {
        val h = makeHierarchy(fleetCommander = 1L, successionQueue = listOf(2L))
        // Only officer 99 alive -- not commander, not in queue
        assertTrue(SuccessionService.isCommandBroken(h, setOf(99L)))
    }

    @Test
    fun `isCommandBroken returns true with empty queue and dead commander`() {
        val h = makeHierarchy(fleetCommander = 1L)
        assertTrue(SuccessionService.isCommandBroken(h, emptySet()))
    }

    // ── OutOfCrcBehavior integration ──

    @Test
    fun `command breakdown triggers retreat for low HP units`() {
        // Create a unit with HP < 30% on broken-command side
        val unit = makeUnit(
            fleetId = 10L, officerId = 99L,
            side = BattleSide.ATTACKER,
            hp = 20, maxHp = 100,  // 20% HP
        )
        // OutOfCrcBehavior with null commander: HP<30% should trigger retreat
        assertFalse(unit.isRetreating)
        OutOfCrcBehavior.processOutOfCrcUnit(unit, null, 0)
        assertTrue(unit.isRetreating)
    }

    @Test
    fun `command breakdown maintains velocity for healthy units`() {
        val unit = makeUnit(
            fleetId = 11L, officerId = 88L,
            side = BattleSide.DEFENDER,
            hp = 80, maxHp = 100,  // 80% HP -- healthy
        )
        unit.velX = 2.5
        unit.velY = 1.0

        OutOfCrcBehavior.processOutOfCrcUnit(unit, null, 0)

        // Healthy unit with null commander: maintain last velocity (no change)
        assertFalse(unit.isRetreating)
        assertEquals(2.5, unit.velX)
        assertEquals(1.0, unit.velY)
    }

    // ── findNextSuccessor tests ──

    @Test
    fun `findNextSuccessor returns designated successor when alive`() {
        val h = makeHierarchy(fleetCommander = 1L, successionQueue = listOf(2L, 3L), designatedSuccessor = 3L)
        assertEquals(3L, SuccessionService.findNextSuccessor(h, setOf(2L, 3L)))
    }

    @Test
    fun `findNextSuccessor falls back to queue when designated dead`() {
        val h = makeHierarchy(fleetCommander = 1L, successionQueue = listOf(2L, 3L), designatedSuccessor = 4L)
        // Designated 4L is dead, so fall back to queue: 2L is first alive
        assertEquals(2L, SuccessionService.findNextSuccessor(h, setOf(2L, 3L)))
    }

    @Test
    fun `findNextSuccessor returns null when nobody alive`() {
        val h = makeHierarchy(fleetCommander = 1L, successionQueue = listOf(2L, 3L))
        assertNull(SuccessionService.findNextSuccessor(h, setOf(99L)))
    }
}
