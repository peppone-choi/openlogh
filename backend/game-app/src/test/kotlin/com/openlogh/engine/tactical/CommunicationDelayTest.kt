package com.openlogh.engine.tactical

import com.openlogh.model.Formation
import com.openlogh.model.UnitStance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Phase 24-32 (gap C17, gin7 매뉴얼 p52):
 * 전술 커맨드 통신 지연 — enqueue 된 커맨드는 0..20 틱 범위의 랜덤 딜레이를
 * 받는다. 본 테스트는 결정적 검증을 위해 `delayedCommandBuffer` 에 직접
 * `DelayedTacticalCommand` 를 집어 넣고, 엔진의 drainCommandBuffer 가 정확한
 * 틱에 promote 하는지 확인한다.
 *
 * 검증 관점:
 *   1. dispatchTick > currentTick 이면 promote 되지 않는다 (보류).
 *   2. dispatchTick == currentTick 이면 promote + apply.
 *   3. dispatchTick < currentTick (과거 예약) 이면 즉시 promote + apply.
 *   4. shouldApplyCommunicationDelay 의 예외 목록(관리 커맨드) 이 정확하다.
 *   5. 여러 delay 된 커맨드가 각자의 dispatch 틱에 맞춰 순차 적용된다.
 */
class CommunicationDelayTest {

    private val engine = TacticalBattleEngine()

    private fun makeUnit(
        fleetId: Long,
        side: BattleSide,
        posX: Double = 100.0,
        posY: Double = 100.0,
        formation: Formation = Formation.MIXED,
    ) = TacticalUnit(
        fleetId = fleetId,
        officerId = fleetId,
        officerName = "Officer $fleetId",
        factionId = if (side == BattleSide.ATTACKER) 1L else 2L,
        side = side,
        posX = posX,
        posY = posY,
        hp = 1000,
        maxHp = 1000,
        ships = 300,
        maxShips = 300,
        training = 80,
        morale = 80,
        leadership = 50,
        command = 50,
        intelligence = 50,
        mobility = 50,
        attack = 50,
        defense = 50,
        unitType = "FLEET",
        formation = formation,
        stance = UnitStance.STATIONED,
    )

    private fun makeState(vararg units: TacticalUnit): TacticalBattleState {
        val state = TacticalBattleState(
            battleId = 1L,
            starSystemId = 1L,
            units = units.toMutableList(),
        )
        // Mark officers online so TacticalAIRunner does not enqueue AI commands.
        state.connectedPlayerOfficerIds.addAll(units.map { it.officerId })
        return state
    }

    @Test
    fun `C17 - command with future dispatchTick stays in delayed buffer`() {
        val unit = makeUnit(1L, BattleSide.ATTACKER)
        val state = makeState(unit)

        // Schedule a SetFormation command for 5 ticks in the future.
        val cmd = TacticalCommand.SetFormation(state.battleId, unit.officerId, Formation.WEDGE)
        state.delayedCommandBuffer.offer(DelayedTacticalCommand(dispatchTick = 5, command = cmd))

        // Advance by 1 tick (currentTick becomes 1 after processTick increments).
        engine.processTick(state, Random(0))

        // At currentTick=1 the command (dispatchTick=5) must NOT be promoted yet.
        assertEquals(1, state.delayedCommandBuffer.size, "delayed command still pending")
        assertEquals(Formation.MIXED, unit.formation, "formation must not change yet")
    }

    @Test
    fun `C17 - command promotes and applies on its dispatch tick`() {
        val unit = makeUnit(1L, BattleSide.ATTACKER)
        val state = makeState(unit)
        val cmd = TacticalCommand.SetFormation(state.battleId, unit.officerId, Formation.WEDGE)
        // dispatchTick = 3 (absolute). Engine will run ticks 1, 2, 3 — promoted on 3.
        state.delayedCommandBuffer.offer(DelayedTacticalCommand(dispatchTick = 3, command = cmd))

        repeat(3) { engine.processTick(state, Random(0)) }

        assertEquals(0, state.delayedCommandBuffer.size, "promoted at tick 3")
        assertEquals(Formation.WEDGE, unit.formation, "formation applied after promotion")
    }

    @Test
    fun `C17 - past dispatchTick promotes immediately on the next tick`() {
        val unit = makeUnit(1L, BattleSide.ATTACKER)
        val state = makeState(unit)
        state.currentTick = 10
        state.tickCount = 10
        val cmd = TacticalCommand.SetFormation(state.battleId, unit.officerId, Formation.WEDGE)
        // Ancient dispatch tick — should still promote.
        state.delayedCommandBuffer.offer(DelayedTacticalCommand(dispatchTick = 2, command = cmd))

        engine.processTick(state, Random(0))

        assertEquals(0, state.delayedCommandBuffer.size)
        assertEquals(Formation.WEDGE, unit.formation)
    }

    @Test
    fun `C17 - excluded command types bypass the delay path`() {
        // Administrative/succession/jamming/conquest 은 지연 대상이 아니다.
        assertFalse(
            TacticalBattleEngine.shouldApplyCommunicationDelay(
                TacticalCommand.AssignSubFleet(1L, 1L, 2L, listOf(1L))
            )
        )
        assertFalse(
            TacticalBattleEngine.shouldApplyCommunicationDelay(
                TacticalCommand.ReassignUnit(1L, 1L, 2L, null)
            )
        )
        assertFalse(
            TacticalBattleEngine.shouldApplyCommunicationDelay(
                TacticalCommand.TriggerJamming(1L, 1L, BattleSide.ATTACKER)
            )
        )
        assertFalse(
            TacticalBattleEngine.shouldApplyCommunicationDelay(
                TacticalCommand.DesignateSuccessor(1L, 1L, 2L)
            )
        )
        assertFalse(
            TacticalBattleEngine.shouldApplyCommunicationDelay(
                TacticalCommand.DelegateCommand(1L, 1L)
            )
        )
    }

    @Test
    fun `C17 - non-excluded command types go through the delay path`() {
        assertTrue(
            TacticalBattleEngine.shouldApplyCommunicationDelay(
                TacticalCommand.SetFormation(1L, 1L, Formation.WEDGE)
            )
        )
        assertTrue(
            TacticalBattleEngine.shouldApplyCommunicationDelay(
                TacticalCommand.Retreat(1L, 1L)
            )
        )
        assertTrue(
            TacticalBattleEngine.shouldApplyCommunicationDelay(
                TacticalCommand.UnitCommand(1L, 1L, command = "MOVE")
            )
        )
    }

    @Test
    fun `C17 - multiple delayed commands promote in order of their dispatch ticks`() {
        val u1 = makeUnit(1L, BattleSide.ATTACKER)
        val u2 = makeUnit(2L, BattleSide.ATTACKER)
        val state = makeState(u1, u2)
        state.delayedCommandBuffer.offer(DelayedTacticalCommand(5, TacticalCommand.SetFormation(1L, u1.officerId, Formation.WEDGE)))
        state.delayedCommandBuffer.offer(DelayedTacticalCommand(2, TacticalCommand.SetFormation(1L, u2.officerId, Formation.THREE_COLUMN)))

        // After tick 2 the u2 formation should be applied but u1 still pending.
        repeat(2) { engine.processTick(state, Random(0)) }
        assertEquals(Formation.THREE_COLUMN, u2.formation)
        assertEquals(Formation.MIXED, u1.formation, "u1 still in delay")
        assertEquals(1, state.delayedCommandBuffer.size, "only u1 still pending")

        // Advance to tick 5 — u1 now applied.
        repeat(3) { engine.processTick(state, Random(0)) }
        assertEquals(Formation.WEDGE, u1.formation)
        assertEquals(0, state.delayedCommandBuffer.size)
    }

    @Test
    fun `C17 - COMMUNICATION_DELAY_MAX_TICKS is pinned at 20`() {
        assertEquals(20, TacticalBattleEngine.COMMUNICATION_DELAY_MAX_TICKS)
    }
}
