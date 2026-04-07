package com.openlogh.engine.tactical

import com.openlogh.model.CommandRange
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CrcValidatorTest {

    // ── Helper ──

    private fun makeUnit(
        fleetId: Long = 1L,
        officerId: Long = 1L,
        posX: Double = 0.0,
        posY: Double = 0.0,
        commandRange: CommandRange = CommandRange(),
        subFleetCommanderId: Long? = null,
        side: BattleSide = BattleSide.ATTACKER,
    ): TacticalUnit = TacticalUnit(
        fleetId = fleetId,
        officerId = officerId,
        officerName = "Officer$officerId",
        factionId = 1L,
        side = side,
        posX = posX,
        posY = posY,
        commandRange = commandRange,
        subFleetCommanderId = subFleetCommanderId,
    )

    private fun makeHierarchy(
        fleetCommander: Long = 10L,
        subCommanders: Map<Long, SubFleet> = emptyMap(),
    ): CommandHierarchy = CommandHierarchy(
        fleetCommander = fleetCommander,
        subCommanders = subCommanders.toMutableMap(),
    )

    private fun makeCommand(officerId: Long, battleId: Long = 1L): TacticalCommand.SetEnergy =
        TacticalCommand.SetEnergy(
            battleId = battleId,
            officerId = officerId,
            allocation = com.openlogh.model.EnergyAllocation.BALANCED,
        )

    // ── isWithinCrc tests ──

    @Nested
    inner class IsWithinCrcTests {

        @Test
        fun `unit at distance less than currentRange returns true`() {
            val commander = makeUnit(
                posX = 0.0, posY = 0.0,
                commandRange = CommandRange(currentRange = 100.0, maxRange = 200.0),
            )
            val target = makeUnit(posX = 50.0, posY = 0.0)
            assertTrue(CrcValidator.isWithinCrc(commander, target))
        }

        @Test
        fun `unit at distance greater than currentRange returns false`() {
            val commander = makeUnit(
                posX = 0.0, posY = 0.0,
                commandRange = CommandRange(currentRange = 100.0, maxRange = 200.0),
            )
            val target = makeUnit(posX = 150.0, posY = 0.0)
            assertFalse(CrcValidator.isWithinCrc(commander, target))
        }

        @Test
        fun `unit at distance exactly equal to currentRange returns true (D-08 binary inclusive)`() {
            val commander = makeUnit(
                posX = 0.0, posY = 0.0,
                commandRange = CommandRange(currentRange = 100.0, maxRange = 200.0),
            )
            val target = makeUnit(posX = 100.0, posY = 0.0)
            assertTrue(CrcValidator.isWithinCrc(commander, target))
        }

        @Test
        fun `commander with hasCommandRange=false returns false`() {
            val commander = makeUnit(
                posX = 0.0, posY = 0.0,
                commandRange = CommandRange(hasCommandRange = false),
            )
            val target = makeUnit(posX = 10.0, posY = 0.0)
            assertFalse(CrcValidator.isWithinCrc(commander, target))
        }

        @Test
        fun `solo ship (no CRC) returns false`() {
            val commander = makeUnit(
                posX = 0.0, posY = 0.0,
                commandRange = CommandRange.create(50, 200.0, isSolo = true),
            )
            val target = makeUnit(posX = 10.0, posY = 0.0)
            assertFalse(CrcValidator.isWithinCrc(commander, target))
        }

        @Test
        fun `CRC at exactly 0 after reset - no units in range`() {
            val commander = makeUnit(
                posX = 0.0, posY = 0.0,
                commandRange = CommandRange(currentRange = 0.0, maxRange = 200.0),
            )
            val target = makeUnit(posX = 1.0, posY = 0.0)
            assertFalse(CrcValidator.isWithinCrc(commander, target))
        }

        @Test
        fun `CRC at 0 - same position unit is within range`() {
            val commander = makeUnit(
                posX = 50.0, posY = 50.0,
                commandRange = CommandRange(currentRange = 0.0, maxRange = 200.0),
            )
            val target = makeUnit(posX = 50.0, posY = 50.0)
            assertTrue(CrcValidator.isWithinCrc(commander, target))
        }

        @Test
        fun `2D distance uses Euclidean formula`() {
            val commander = makeUnit(
                posX = 0.0, posY = 0.0,
                commandRange = CommandRange(currentRange = 100.0, maxRange = 200.0),
            )
            // Distance = sqrt(60^2 + 80^2) = 100.0 exactly
            val target = makeUnit(posX = 60.0, posY = 80.0)
            assertTrue(CrcValidator.isWithinCrc(commander, target))
        }
    }

    // ── isCommandReachable tests ──

    @Nested
    inner class IsCommandReachableTests {

        @Test
        fun `self-command always returns true regardless of CRC (Pitfall 1 avoidance)`() {
            val unit = makeUnit(
                officerId = 5L,
                posX = 500.0,
                commandRange = CommandRange(currentRange = 0.0, maxRange = 200.0),
            )
            val cmd = makeCommand(officerId = 5L)
            val hierarchy = makeHierarchy(fleetCommander = 10L)

            assertTrue(CrcValidator.isCommandReachable(cmd, unit, hierarchy, listOf(unit)))
        }

        @Test
        fun `fleet commander to own CRC unit returns true`() {
            val commander = makeUnit(
                officerId = 10L, posX = 0.0, posY = 0.0,
                commandRange = CommandRange(currentRange = 200.0, maxRange = 200.0),
            )
            val target = makeUnit(
                officerId = 20L, fleetId = 2L, posX = 100.0, posY = 0.0,
            )
            val cmd = makeCommand(officerId = 10L)
            val hierarchy = makeHierarchy(fleetCommander = 10L)

            assertTrue(CrcValidator.isCommandReachable(cmd, target, hierarchy, listOf(commander, target)))
        }

        @Test
        fun `fleet commander to out-of-CRC unit returns false`() {
            val commander = makeUnit(
                officerId = 10L, posX = 0.0, posY = 0.0,
                commandRange = CommandRange(currentRange = 50.0, maxRange = 200.0),
            )
            val target = makeUnit(
                officerId = 20L, fleetId = 2L, posX = 300.0, posY = 0.0,
            )
            val cmd = makeCommand(officerId = 10L)
            val hierarchy = makeHierarchy(fleetCommander = 10L)

            assertFalse(CrcValidator.isCommandReachable(cmd, target, hierarchy, listOf(commander, target)))
        }

        @Test
        fun `sub-fleet commander to own assigned unit within CRC returns true`() {
            val subCommander = makeUnit(
                officerId = 20L, fleetId = 2L, posX = 100.0, posY = 0.0,
                commandRange = CommandRange(currentRange = 150.0, maxRange = 200.0),
            )
            val target = makeUnit(
                officerId = 30L, fleetId = 3L, posX = 200.0, posY = 0.0,
                subFleetCommanderId = 20L,
            )
            val cmd = makeCommand(officerId = 20L)
            val subFleet = SubFleet(
                commanderId = 20L, commanderName = "Sub", unitIds = listOf(3L), commanderRank = 5,
            )
            val hierarchy = makeHierarchy(fleetCommander = 10L, subCommanders = mapOf(20L to subFleet))

            assertTrue(CrcValidator.isCommandReachable(cmd, target, hierarchy, listOf(subCommander, target)))
        }

        @Test
        fun `sub-fleet commander to non-assigned unit returns false`() {
            val subCommander = makeUnit(
                officerId = 20L, fleetId = 2L, posX = 100.0, posY = 0.0,
                commandRange = CommandRange(currentRange = 150.0, maxRange = 200.0),
            )
            val target = makeUnit(
                officerId = 30L, fleetId = 3L, posX = 150.0, posY = 0.0,
                subFleetCommanderId = 99L, // assigned to a different sub-fleet
            )
            val cmd = makeCommand(officerId = 20L)
            val subFleet = SubFleet(
                commanderId = 20L, commanderName = "Sub", unitIds = listOf(4L), commanderRank = 5,
            )
            val hierarchy = makeHierarchy(fleetCommander = 10L, subCommanders = mapOf(20L to subFleet))

            assertFalse(CrcValidator.isCommandReachable(cmd, target, hierarchy, listOf(subCommander, target)))
        }

        @Test
        fun `fleet commander can issue fleet-wide orders to units with subFleetCommanderId within CRC`() {
            val commander = makeUnit(
                officerId = 10L, posX = 0.0, posY = 0.0,
                commandRange = CommandRange(currentRange = 200.0, maxRange = 200.0),
            )
            val target = makeUnit(
                officerId = 30L, fleetId = 3L, posX = 100.0, posY = 0.0,
                subFleetCommanderId = 20L, // has a sub-fleet commander but fleet cmdr can still order
            )
            val cmd = makeCommand(officerId = 10L)
            val hierarchy = makeHierarchy(fleetCommander = 10L)

            assertTrue(CrcValidator.isCommandReachable(cmd, target, hierarchy, listOf(commander, target)))
        }

        @Test
        fun `unknown officer (not commander, not sub-commander) returns false`() {
            val unknownOfficer = makeUnit(officerId = 99L, posX = 0.0, posY = 0.0)
            val target = makeUnit(officerId = 30L, fleetId = 3L, posX = 50.0, posY = 0.0)
            val cmd = makeCommand(officerId = 99L)
            val hierarchy = makeHierarchy(fleetCommander = 10L)

            assertFalse(CrcValidator.isCommandReachable(cmd, target, hierarchy, listOf(unknownOfficer, target)))
        }
    }

    // ── computeCrcRange tests ──

    @Nested
    inner class ComputeCrcRangeTests {

        @Test
        fun `command stat 50 produces maxRange 200 and expansionRate 1_0`() {
            val range = CrcValidator.computeCrcRange(50)
            assertEquals(200.0, range.maxRange, 0.001)
            assertEquals(1.0, range.expansionRate, 0.001)
            assertEquals(0.0, range.currentRange)
            assertTrue(range.hasCommandRange)
        }

        @Test
        fun `command stat 100 produces maxRange 350 and expansionRate 1_5`() {
            val range = CrcValidator.computeCrcRange(100)
            assertEquals(350.0, range.maxRange, 0.001)
            assertEquals(1.5, range.expansionRate, 0.001)
        }

        @Test
        fun `command stat 0 produces minimum values`() {
            val range = CrcValidator.computeCrcRange(0)
            assertEquals(50.0, range.maxRange, 0.001)
            assertEquals(0.5, range.expansionRate, 0.001)
        }
    }
}
