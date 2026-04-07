package com.openlogh.engine.tactical

import com.openlogh.model.CommandRange
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class OutOfCrcBehaviorTest {

    // ── Helper ──

    private fun makeUnit(
        fleetId: Long = 1L,
        officerId: Long = 1L,
        posX: Double = 500.0,
        posY: Double = 300.0,
        hp: Int = 100,
        maxHp: Int = 100,
        side: BattleSide = BattleSide.ATTACKER,
        isAlive: Boolean = true,
        isRetreating: Boolean = false,
        velX: Double = 2.0,
        velY: Double = 1.0,
        lastCommandTick: Int = 0,
    ): TacticalUnit = TacticalUnit(
        fleetId = fleetId,
        officerId = officerId,
        officerName = "Officer$officerId",
        factionId = 1L,
        side = side,
        posX = posX,
        posY = posY,
        velX = velX,
        velY = velY,
        hp = hp,
        maxHp = maxHp,
        isAlive = isAlive,
        isRetreating = isRetreating,
        lastCommandTick = lastCommandTick,
    )

    // ── Maintain last order tests ──

    @Nested
    inner class MaintainLastOrderTests {

        @Test
        fun `unit outside CRC with HP 30 percent or more - velocity unchanged`() {
            val unit = makeUnit(hp = 50, maxHp = 100, velX = 2.0, velY = 1.0)
            val originalVelX = unit.velX
            val originalVelY = unit.velY

            OutOfCrcBehavior.processOutOfCrcUnit(unit, commanderUnit = null, currentTick = 10)

            assertEquals(originalVelX, unit.velX, 0.001)
            assertEquals(originalVelY, unit.velY, 0.001)
        }

        @Test
        fun `unit with HP exactly 30 percent - velocity unchanged (threshold not reached)`() {
            val unit = makeUnit(hp = 30, maxHp = 100, velX = 3.0, velY = -1.0)
            val originalVelX = unit.velX
            val originalVelY = unit.velY

            OutOfCrcBehavior.processOutOfCrcUnit(unit, commanderUnit = null, currentTick = 10)

            assertEquals(originalVelX, unit.velX, 0.001)
            assertEquals(originalVelY, unit.velY, 0.001)
        }
    }

    // ── AI retreat tests ──

    @Nested
    inner class AiRetreatTests {

        @Test
        fun `unit with HP less than 30 percent and not retreating - AI retreat triggered`() {
            val unit = makeUnit(hp = 29, maxHp = 100, side = BattleSide.ATTACKER)

            OutOfCrcBehavior.processOutOfCrcUnit(unit, commanderUnit = null, currentTick = 10)

            assertTrue(unit.isRetreating)
        }

        @Test
        fun `ATTACKER side retreats toward posX=0 (left edge)`() {
            val unit = makeUnit(hp = 20, maxHp = 100, posX = 500.0, side = BattleSide.ATTACKER)

            OutOfCrcBehavior.processOutOfCrcUnit(unit, commanderUnit = null, currentTick = 10)

            assertTrue(unit.isRetreating)
            assertTrue(unit.velX < 0.0, "ATTACKER should move left (toward 0)")
            assertEquals(0.0, unit.velY, 0.001, "Retreat should be horizontal only")
        }

        @Test
        fun `DEFENDER side retreats toward posX=1000 (right edge)`() {
            val unit = makeUnit(hp = 20, maxHp = 100, posX = 500.0, side = BattleSide.DEFENDER)

            OutOfCrcBehavior.processOutOfCrcUnit(unit, commanderUnit = null, currentTick = 10)

            assertTrue(unit.isRetreating)
            assertTrue(unit.velX > 0.0, "DEFENDER should move right (toward 1000)")
            assertEquals(0.0, unit.velY, 0.001, "Retreat should be horizontal only")
        }

        @Test
        fun `retreat speed is 80 percent of BASE_SPEED`() {
            val unit = makeUnit(hp = 10, maxHp = 100, posX = 500.0, side = BattleSide.ATTACKER)
            val expectedSpeed = TacticalBattleEngine.BASE_SPEED * OutOfCrcBehavior.RETREAT_SPEED_FACTOR

            OutOfCrcBehavior.processOutOfCrcUnit(unit, commanderUnit = null, currentTick = 10)

            assertEquals(expectedSpeed, kotlin.math.abs(unit.velX), 0.001)
        }

        @Test
        fun `unit already retreating - no re-trigger`() {
            val unit = makeUnit(hp = 10, maxHp = 100, isRetreating = true, velX = -5.0, velY = 0.0)
            val originalVelX = unit.velX

            OutOfCrcBehavior.processOutOfCrcUnit(unit, commanderUnit = null, currentTick = 10)

            // Should NOT change velocity since already retreating
            assertEquals(originalVelX, unit.velX, 0.001)
        }
    }

    // ── Dead unit tests ──

    @Nested
    inner class DeadUnitTests {

        @Test
        fun `dead unit is skipped`() {
            val unit = makeUnit(hp = 0, maxHp = 100, isAlive = false, velX = 2.0, velY = 1.0)
            val originalVelX = unit.velX
            val originalVelY = unit.velY

            OutOfCrcBehavior.processOutOfCrcUnit(unit, commanderUnit = null, currentTick = 10)

            assertEquals(originalVelX, unit.velX, 0.001)
            assertEquals(originalVelY, unit.velY, 0.001)
            assertFalse(unit.isRetreating)
        }
    }

    // ── Move-toward-commander fallback (Pitfall 5 avoidance) ──

    @Nested
    inner class MoveTowardCommanderTests {

        @Test
        fun `unit outside CRC for more than MAX_AUTONOMOUS_TICKS moves toward commander`() {
            val unit = makeUnit(
                hp = 80, maxHp = 100,
                posX = 800.0, posY = 300.0,
                lastCommandTick = 0,
                velX = 2.0, velY = 0.0,
            )
            val commander = makeUnit(
                officerId = 10L, posX = 200.0, posY = 300.0,
            )
            val currentTick = OutOfCrcBehavior.MAX_AUTONOMOUS_TICKS + 1

            OutOfCrcBehavior.processOutOfCrcUnit(unit, commanderUnit = commander, currentTick = currentTick)

            // Should move toward commander (posX=200), so velX should be negative
            assertTrue(unit.velX < 0.0, "Unit should move toward commander (left)")
        }

        @Test
        fun `move-toward-commander speed is 50 percent of BASE_SPEED`() {
            val unit = makeUnit(
                hp = 80, maxHp = 100,
                posX = 800.0, posY = 300.0,
                lastCommandTick = 0,
            )
            val commander = makeUnit(
                officerId = 10L, posX = 200.0, posY = 300.0,
            )
            val currentTick = OutOfCrcBehavior.MAX_AUTONOMOUS_TICKS + 1

            OutOfCrcBehavior.processOutOfCrcUnit(unit, commanderUnit = commander, currentTick = currentTick)

            val speed = kotlin.math.sqrt(unit.velX * unit.velX + unit.velY * unit.velY)
            val expectedSpeed = TacticalBattleEngine.BASE_SPEED * 0.5
            assertEquals(expectedSpeed, speed, 0.001)
        }

        @Test
        fun `move-toward-commander not triggered when commander is null`() {
            val unit = makeUnit(
                hp = 80, maxHp = 100,
                posX = 800.0, posY = 300.0,
                lastCommandTick = 0,
                velX = 2.0, velY = 1.0,
            )
            val currentTick = OutOfCrcBehavior.MAX_AUTONOMOUS_TICKS + 1

            OutOfCrcBehavior.processOutOfCrcUnit(unit, commanderUnit = null, currentTick = currentTick)

            // Without commander, should maintain last order (velocity unchanged)
            assertEquals(2.0, unit.velX, 0.001)
            assertEquals(1.0, unit.velY, 0.001)
        }

        @Test
        fun `move-toward-commander not triggered when within MAX_AUTONOMOUS_TICKS`() {
            val unit = makeUnit(
                hp = 80, maxHp = 100,
                posX = 800.0, posY = 300.0,
                lastCommandTick = 50,
                velX = 2.0, velY = 1.0,
            )
            val commander = makeUnit(officerId = 10L, posX = 200.0, posY = 300.0)
            val currentTick = 100 // only 50 ticks since last command, within MAX_AUTONOMOUS_TICKS

            OutOfCrcBehavior.processOutOfCrcUnit(unit, commanderUnit = commander, currentTick = currentTick)

            // Should maintain last order
            assertEquals(2.0, unit.velX, 0.001)
            assertEquals(1.0, unit.velY, 0.001)
        }

        @Test
        fun `HP retreat takes priority over move-toward-commander`() {
            val unit = makeUnit(
                hp = 10, maxHp = 100,
                posX = 800.0, posY = 300.0,
                lastCommandTick = 0,
                side = BattleSide.ATTACKER,
            )
            val commander = makeUnit(officerId = 10L, posX = 900.0, posY = 300.0)
            val currentTick = OutOfCrcBehavior.MAX_AUTONOMOUS_TICKS + 1

            OutOfCrcBehavior.processOutOfCrcUnit(unit, commanderUnit = commander, currentTick = currentTick)

            // Retreat takes priority: ATTACKER retreats toward posX=0
            assertTrue(unit.isRetreating)
            assertTrue(unit.velX < 0.0, "Should retreat toward left edge, not toward commander")
        }
    }
}
