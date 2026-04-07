package com.openlogh.engine.tactical

import com.openlogh.model.EnergyAllocation
import com.openlogh.model.UnitStance
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for ENGINE-02: command buffer drain.
 *
 * Tests verify the behavioral contract for the command buffer pattern:
 * WebSocket commands are enqueued into ConcurrentLinkedQueue<TacticalCommand>,
 * then drained and applied once per tick by drainCommandBuffer(state).
 */
class CommandBufferTest {

    private val engine = TacticalBattleEngine()

    private fun makeUnit(
        fleetId: Long,
        side: BattleSide,
        morale: Int = 80,
        stance: UnitStance = UnitStance.NAVIGATION,
    ) = TacticalUnit(
        fleetId = fleetId,
        officerId = fleetId,
        officerName = "Officer $fleetId",
        factionId = if (side == BattleSide.ATTACKER) 1L else 2L,
        side = side,
        posX = 100.0,
        posY = 100.0,
        hp = 1000,
        maxHp = 1000,
        ships = 300,
        maxShips = 300,
        training = 80,
        morale = morale,
        leadership = 50,
        command = 50,
        intelligence = 50,
        mobility = 50,
        attack = 50,
        defense = 50,
        stance = stance,
    )

    private fun makeState(vararg units: TacticalUnit) = TacticalBattleState(
        battleId = 1L,
        starSystemId = 1L,
        units = units.toMutableList(),
    )

    /**
     * Enqueued SetEnergy command should be drained and applied to the unit's energy allocation.
     */
    @Test
    fun `enqueued SetEnergy command is drained and applied to unit energy allocation`() {
        val unit = makeUnit(1L, BattleSide.ATTACKER)
        val state = makeState(unit)

        val aggressiveEnergy = EnergyAllocation.AGGRESSIVE
        state.commandBuffer.add(TacticalCommand.SetEnergy(battleId = 1L, officerId = 1L, allocation = aggressiveEnergy))

        engine.drainCommandBuffer(state)
        assertEquals(aggressiveEnergy, state.units[0].energy)
    }

    /**
     * Enqueued SetStance command should be drained and applied to the unit's stance.
     */
    @Test
    fun `enqueued SetStance command is drained and applied to unit stance`() {
        val unit = makeUnit(1L, BattleSide.ATTACKER, stance = UnitStance.NAVIGATION)
        val state = makeState(unit)

        state.commandBuffer.add(TacticalCommand.SetStance(battleId = 1L, officerId = 1L, stance = UnitStance.COMBAT))

        engine.drainCommandBuffer(state)
        assertEquals(UnitStance.COMBAT, state.units[0].stance)
    }

    /**
     * Enqueued Retreat command should set the unit's isRetreating flag to true.
     */
    @Test
    fun `enqueued Retreat command sets unit isRetreating to true`() {
        val unit = makeUnit(1L, BattleSide.ATTACKER)
        val state = makeState(unit)

        state.commandBuffer.add(TacticalCommand.Retreat(battleId = 1L, officerId = 1L))

        engine.drainCommandBuffer(state)
        assertTrue(state.units[0].isRetreating)
    }

    /**
     * Multiple commands enqueued before a tick should all be applied in FIFO order.
     */
    @Test
    fun `multiple commands enqueued before tick are all applied in order`() {
        val unit = makeUnit(1L, BattleSide.ATTACKER)
        val state = makeState(unit)

        // First: set aggressive energy, then: set combat stance
        state.commandBuffer.add(TacticalCommand.SetEnergy(battleId = 1L, officerId = 1L, allocation = EnergyAllocation.AGGRESSIVE))
        state.commandBuffer.add(TacticalCommand.SetStance(battleId = 1L, officerId = 1L, stance = UnitStance.COMBAT))

        engine.drainCommandBuffer(state)
        assertEquals(EnergyAllocation.AGGRESSIVE, state.units[0].energy)
        assertEquals(UnitStance.COMBAT, state.units[0].stance)
        assertTrue(state.commandBuffer.isEmpty(), "Buffer should be empty after drain")
    }

    /**
     * Command targeting a dead unit (isAlive=false) should be silently ignored.
     */
    @Test
    fun `command for dead unit is silently ignored`() {
        val unit = makeUnit(1L, BattleSide.ATTACKER).apply { isAlive = false }
        val state = makeState(unit)

        state.commandBuffer.add(TacticalCommand.SetEnergy(battleId = 1L, officerId = 1L, allocation = EnergyAllocation.AGGRESSIVE))

        engine.drainCommandBuffer(state)
        // Dead unit's energy should remain default (BALANCED), not changed to AGGRESSIVE
        assertEquals(EnergyAllocation.BALANCED, state.units[0].energy)
        assertTrue(state.commandBuffer.isEmpty(), "Buffer should be empty even for ignored commands")
    }
}
