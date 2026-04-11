package com.openlogh.engine.tactical

import com.openlogh.model.EnergyAllocation
import com.openlogh.model.Formation
import com.openlogh.model.UnitStance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Phase 24-33 (gap C12, gin7 매뉴얼 p52):
 * 사격(FIRE) 커맨드와 공격(ATTACK) 커맨드의 구분을 커버한다.
 *   · ATTACK: 지속 타겟 (targetFleetId) — 다음 ATTACK/자동 선택이 덮어쓸 때까지 유지.
 *   · FIRE: 일회성 타겟 (fireOnceTargetId) — processCombat 가 한 번 쓰고 즉시 clear.
 */
class FireVsAttackTest {

    private val engine = TacticalBattleEngine()

    private fun makeUnit(
        fleetId: Long,
        side: BattleSide,
        posX: Double = 100.0,
        posY: Double = 100.0,
    ) = TacticalUnit(
        fleetId = fleetId,
        officerId = fleetId,
        officerName = "Officer $fleetId",
        factionId = if (side == BattleSide.ATTACKER) 1L else 2L,
        side = side,
        posX = posX,
        posY = posY,
        hp = 2000,
        maxHp = 2000,
        ships = 300,
        maxShips = 300,
        training = 80,
        morale = 80,
        leadership = 80,
        command = 80,
        intelligence = 60,
        mobility = 50,
        attack = 80,
        defense = 40,
        energy = EnergyAllocation(beam = 60, gun = 0, shield = 10, engine = 10, warp = 10, sensor = 10),
        unitType = "FLEET",
        formation = Formation.MIXED,
        stance = UnitStance.COMBAT,
    )

    private fun makeState(vararg units: TacticalUnit): TacticalBattleState {
        val state = TacticalBattleState(
            battleId = 1L,
            starSystemId = 1L,
            units = units.toMutableList(),
        )
        state.connectedPlayerOfficerIds.addAll(units.map { it.officerId })
        return state
    }

    @Test
    fun `ATTACK sets persistent target on the unit`() {
        val attacker = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0)
        val d1 = makeUnit(2L, BattleSide.DEFENDER, posX = 100.0, posY = 0.0)
        val d2 = makeUnit(3L, BattleSide.DEFENDER, posX = 50.0, posY = 0.0)
        val state = makeState(attacker, d1, d2)
        state.commandBuffer.add(
            TacticalCommand.UnitCommand(
                battleId = 1L, officerId = attacker.officerId,
                command = "ATTACK", targetFleetId = d1.fleetId,
            )
        )
        engine.processTick(state, Random(42))

        assertEquals(d1.fleetId, attacker.targetFleetId, "ATTACK must set persistent target")
        assertNull(attacker.fireOnceTargetId, "ATTACK must not touch the fire-once slot")
    }

    @Test
    fun `FIRE sets a one-shot target that clears after the next combat step`() {
        val attacker = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0)
        val d1 = makeUnit(2L, BattleSide.DEFENDER, posX = 100.0, posY = 0.0)
        val state = makeState(attacker, d1)
        state.commandBuffer.add(
            TacticalCommand.UnitCommand(
                battleId = 1L, officerId = attacker.officerId,
                command = "FIRE", targetFleetId = d1.fleetId,
            )
        )
        engine.processTick(state, Random(42))

        // processCombat runs during the same tick as the FIRE dispatch — fireOnceTargetId
        // should be null after consumption.
        assertNull(attacker.fireOnceTargetId, "fire-once slot must be cleared after combat step")
        // Damage should have been dealt to the specified target.
        assertTrue(d1.hp < 2000, "defender must take damage from the FIRE command")
    }

    @Test
    fun `FIRE does not touch persistent targetFleetId`() {
        val attacker = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0).also {
            it.targetFleetId = 99L
        }
        val d1 = makeUnit(2L, BattleSide.DEFENDER, posX = 100.0, posY = 0.0)
        val state = makeState(attacker, d1)
        state.commandBuffer.add(
            TacticalCommand.UnitCommand(
                battleId = 1L, officerId = attacker.officerId,
                command = "FIRE", targetFleetId = d1.fleetId,
            )
        )
        engine.processTick(state, Random(42))

        // FIRE must not overwrite the persistent ATTACK target.
        assertEquals(99L, attacker.targetFleetId,
            "FIRE must not clobber the unit's persistent ATTACK target")
    }

    @Test
    fun `FIRE target takes priority over persistent ATTACK target for this step`() {
        val attacker = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0)
        val persistentTarget = makeUnit(2L, BattleSide.DEFENDER, posX = 100.0, posY = 50.0)
        val fireTarget = makeUnit(3L, BattleSide.DEFENDER, posX = 100.0, posY = -50.0)
        val state = makeState(attacker, persistentTarget, fireTarget)

        // Pre-set persistent target and schedule a FIRE that should preempt it for one step.
        attacker.targetFleetId = persistentTarget.fleetId
        state.commandBuffer.add(
            TacticalCommand.UnitCommand(
                battleId = 1L, officerId = attacker.officerId,
                command = "FIRE", targetFleetId = fireTarget.fleetId,
            )
        )
        engine.processTick(state, Random(42))

        // FIRE target must have taken the damage this step.
        assertTrue(fireTarget.hp < 2000, "FIRE target must receive the shot")
        // Persistent target should remain untouched by damage this step.
        assertEquals(2000, persistentTarget.hp, "persistent ATTACK target must be skipped this step")
        // fireOnceTargetId cleared, persistent targetFleetId preserved.
        assertNull(attacker.fireOnceTargetId)
        assertEquals(persistentTarget.fleetId, attacker.targetFleetId)
    }

    @Test
    fun `FIRE with non-existent target cleanly clears the one-shot slot`() {
        val attacker = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0)
        val d1 = makeUnit(2L, BattleSide.DEFENDER, posX = 100.0, posY = 0.0)
        val state = makeState(attacker, d1)
        // Enqueue FIRE with a non-existent target id.
        state.commandBuffer.add(
            TacticalCommand.UnitCommand(
                battleId = 1L, officerId = attacker.officerId,
                command = "FIRE", targetFleetId = 9999L,
            )
        )
        engine.processTick(state, Random(42))
        assertNull(attacker.fireOnceTargetId,
            "fire-once slot must not linger when the target id does not exist")
    }

    @Test
    fun `FIRE without combat range fallback still consumes the one-shot slot`() {
        // Attacker far from any enemy — no fallback target resolvable.
        val attacker = makeUnit(1L, BattleSide.ATTACKER, posX = 0.0, posY = 0.0)
        val farDefender = makeUnit(2L, BattleSide.DEFENDER, posX = 5000.0, posY = 0.0)
        val state = makeState(attacker, farDefender)
        state.commandBuffer.add(
            TacticalCommand.UnitCommand(
                battleId = 1L, officerId = attacker.officerId,
                command = "FIRE", targetFleetId = farDefender.fleetId,
            )
        )
        engine.processTick(state, Random(42))
        // Out-of-range fire: slot should still be cleared so it doesn't haunt future steps.
        assertNull(attacker.fireOnceTargetId)
    }
}
