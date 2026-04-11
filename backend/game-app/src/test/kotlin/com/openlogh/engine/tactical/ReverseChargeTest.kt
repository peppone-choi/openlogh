package com.openlogh.engine.tactical

import com.openlogh.model.EnergyAllocation
import com.openlogh.model.Formation
import com.openlogh.model.UnitStance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Phase 24-25 (gap C9, gin7 매뉴얼 p52):
 * 반전(REVERSE) 커맨드는 명령 수신 후 10 초 대기 뒤에 실제 선회가 일어난다.
 * 엔진 tick rate 은 1 tick = 1 초이므로 10 ticks = 10 초.
 *
 * 테스트 관점:
 *   1. REVERSE 커맨드 직후 reverseChargeTicksRemaining = 10.
 *   2. 9 틱 경과까지는 velocity 가 변하지 않는다 (charge 중).
 *   3. 10 번째 틱에서 counter 가 0 이 되며 velocity 가 반전된다.
 *   4. charge 중 재명령이 들어와도 counter 는 재설정되지 않는다.
 *   5. charge 완료 시 reverse_complete 이벤트가 battle log 에 남는다.
 *
 * 주의: 기본 `processMovement` 은 유닛의 가장 가까운 적으로 방향을 재조정한다.
 * 본 테스트에서는 해당 부작용을 피하기 위해 stance = STATIONED 로 둬서
 * processMovement 의 `stance.canMove` 가드에 걸리도록 한다.
 */
class ReverseChargeTest {

    private val engine = TacticalBattleEngine()

    private fun stationaryUnit(
        fleetId: Long,
        side: BattleSide,
        posX: Double,
        posY: Double,
        velX: Double,
        velY: Double,
        mobility: Int = 50,
    ) = TacticalUnit(
        fleetId = fleetId,
        officerId = fleetId,
        officerName = "Officer $fleetId",
        factionId = if (side == BattleSide.ATTACKER) 1L else 2L,
        side = side,
        posX = posX,
        posY = posY,
        velX = velX,
        velY = velY,
        hp = 1000,
        maxHp = 1000,
        ships = 300,
        maxShips = 300,
        training = 80,
        morale = 80,
        leadership = 50,
        command = 50,
        intelligence = 50,
        mobility = mobility,
        attack = 50,
        defense = 50,
        energy = EnergyAllocation(beam = 40, gun = 10, shield = 10, engine = 10, warp = 10, sensor = 20),
        formation = Formation.MIXED,
        // STATIONED 는 processMovement 가드에 걸려 velocity 가 재계산되지 않는다.
        // NAVIGATION + 1-unit state → processMovement bails via `closestEnemy ?: return`,
        // so velocity stays exactly what we put in until the charge flips it.
        stance = UnitStance.NAVIGATION,
    )

    private fun makeState(vararg units: TacticalUnit): TacticalBattleState {
        val state = TacticalBattleState(
            battleId = 1L,
            starSystemId = 1L,
            units = units.toMutableList(),
        )
        // Mark every unit as online-player-controlled so TacticalAIRunner skips
        // it and does not enqueue AI-issued MOVE/ATTACK commands that would
        // overwrite the velocity we are asserting on.
        state.connectedPlayerOfficerIds.addAll(units.map { it.officerId })
        return state
    }

    private fun sendReverse(state: TacticalBattleState, officerId: Long) {
        state.commandBuffer.add(
            TacticalCommand.UnitCommand(
                battleId = state.battleId,
                officerId = officerId,
                command = "REVERSE",
            )
        )
    }

    @Test
    fun `REVERSE command sets charge to 10 on dispatch`() {
        val unit = stationaryUnit(1L, BattleSide.ATTACKER, posX = 300.0, posY = 300.0, velX = 2.0, velY = 0.0)
        val state = makeState(unit)
        sendReverse(state, unit.officerId)
        engine.processTick(state, Random(42))
        // tick 0 during processing: command applies (reverseCharge = 10), step 0 decrements to 9.
        assertEquals(9, unit.reverseChargeTicksRemaining,
            "after the dispatching tick, charge should be REVERSE_PREP_TICKS - 1 = 9")
        // Velocity should NOT be flipped yet.
        assertEquals(2.0, unit.velX, 0.001, "velocity must not change until charge completes")
    }

    @Test
    fun `velocity stays unchanged during charge ticks 1 to 9`() {
        val unit = stationaryUnit(1L, BattleSide.ATTACKER, posX = 300.0, posY = 300.0, velX = 2.0, velY = -1.0)
        val state = makeState(unit)
        sendReverse(state, unit.officerId)

        repeat(9) { engine.processTick(state, Random(42)) }
        // After 9 ticks, charge should be 1 (10 - 9 ... wait, dispatch-tick itself decremented once, so after 9 total ticks counter = 10 - 9 = 1)
        // Actually the dispatch happens during tick 1 (where the reverse command applies) and step 0 decrements
        // to 9. After 9 total processTick calls, counter = 1.
        assertEquals(1, unit.reverseChargeTicksRemaining)
        assertEquals(2.0, unit.velX, 0.001, "velocity locked during charge")
        assertEquals(-1.0, unit.velY, 0.001)
    }

    @Test
    fun `velocity flips on the 10th tick and mobility 50 means neutral scale`() {
        val unit = stationaryUnit(1L, BattleSide.ATTACKER, posX = 300.0, posY = 300.0,
            velX = 2.0, velY = -1.0, mobility = 50)
        val state = makeState(unit)
        sendReverse(state, unit.officerId)

        repeat(10) { engine.processTick(state, Random(42)) }
        assertEquals(0, unit.reverseChargeTicksRemaining, "charge complete")
        // mobility 50 → factor = 1.0, so velocity simply flips sign.
        assertEquals(-2.0, unit.velX, 0.001)
        assertEquals(1.0, unit.velY, 0.001)
    }

    @Test
    fun `high mobility speeds up the reverse flip`() {
        val unit = stationaryUnit(1L, BattleSide.ATTACKER, posX = 300.0, posY = 300.0,
            velX = 2.0, velY = 0.0, mobility = 100)
        val state = makeState(unit)
        sendReverse(state, unit.officerId)
        repeat(10) { engine.processTick(state, Random(42)) }
        // mobility 100 → factor clamps to 1.5
        assertEquals(-3.0, unit.velX, 0.001,
            "mobility 100 applies 1.5× factor after flip")
    }

    @Test
    fun `low mobility slows the reverse flip`() {
        val unit = stationaryUnit(1L, BattleSide.ATTACKER, posX = 300.0, posY = 300.0,
            velX = 2.0, velY = 0.0, mobility = 0)
        val state = makeState(unit)
        sendReverse(state, unit.officerId)
        repeat(10) { engine.processTick(state, Random(42)) }
        // mobility 0 → factor clamps to 0.5
        assertEquals(-1.0, unit.velX, 0.001, "mobility 0 applies 0.5× factor after flip")
    }

    @Test
    fun `reverse_complete battle event is emitted when charge completes`() {
        val unit = stationaryUnit(1L, BattleSide.ATTACKER, posX = 300.0, posY = 300.0, velX = 2.0, velY = 0.0)
        val state = makeState(unit)
        sendReverse(state, unit.officerId)
        repeat(10) { engine.processTick(state, Random(42)) }
        val evt = state.tickEvents.firstOrNull { it.type == "reverse_complete" }
        assertTrue(evt != null, "reverse_complete event must be emitted on flip")
    }

    @Test
    fun `re-issuing REVERSE while charging does not reset the counter`() {
        val unit = stationaryUnit(1L, BattleSide.ATTACKER, posX = 300.0, posY = 300.0, velX = 2.0, velY = 0.0)
        val state = makeState(unit)
        sendReverse(state, unit.officerId)
        engine.processTick(state, Random(42))
        assertEquals(9, unit.reverseChargeTicksRemaining)

        // Re-issue REVERSE mid-charge.
        sendReverse(state, unit.officerId)
        engine.processTick(state, Random(42))
        // Counter should be 8 (one more decrement), not reset to 10 or 9.
        assertEquals(8, unit.reverseChargeTicksRemaining,
            "re-issued REVERSE during charge must not restart the timer")
    }
}
