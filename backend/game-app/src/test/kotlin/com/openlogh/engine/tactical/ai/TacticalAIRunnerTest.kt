package com.openlogh.engine.tactical.ai

import com.openlogh.engine.ai.PersonalityTrait
import com.openlogh.engine.tactical.BattleSide
import com.openlogh.engine.tactical.TacticalBattleState
import com.openlogh.engine.tactical.TacticalUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Integration tests for TacticalAIRunner within the tick loop.
 *
 * Verifies:
 * - Online player units are skipped
 * - Dead/retreating units are skipped
 * - 10-tick interval gate works
 * - AI evaluation fires and enqueues commands
 * - triggerImmediateReeval resets timer and triggers next tick
 */
class TacticalAIRunnerTest {

    private lateinit var state: TacticalBattleState

    private fun makeUnit(
        fleetId: Long,
        officerId: Long,
        side: BattleSide,
        isAlive: Boolean = true,
        isRetreating: Boolean = false,
        lastAIEvalTick: Int = -10,
        hp: Int = 100,
        maxHp: Int = 100,
        ships: Int = 300,
        maxShips: Int = 300,
        posX: Double = 500.0,
        posY: Double = 300.0,
    ): TacticalUnit = TacticalUnit(
        fleetId = fleetId,
        officerId = officerId,
        officerName = "Officer$officerId",
        factionId = if (side == BattleSide.ATTACKER) 1L else 2L,
        side = side,
        posX = posX,
        posY = posY,
        hp = hp,
        maxHp = maxHp,
        ships = ships,
        maxShips = maxShips,
        isAlive = isAlive,
        isRetreating = isRetreating,
        lastAIEvalTick = lastAIEvalTick,
        personality = PersonalityTrait.BALANCED,
        missionObjective = MissionObjective.SWEEP,
        anchorX = posX,
        anchorY = posY,
        attack = 50,
        defense = 50,
        morale = 80,
        training = 50,
    )

    private fun makeState(
        units: List<TacticalUnit>,
        currentTick: Int = 10,
        connectedPlayers: Set<Long> = emptySet(),
    ): TacticalBattleState = TacticalBattleState(
        battleId = 1L,
        starSystemId = 1L,
        units = units.toMutableList(),
        currentTick = currentTick,
        tickCount = currentTick,
        connectedPlayerOfficerIds = connectedPlayers.toMutableSet(),
    )

    @BeforeEach
    fun setup() {
        state = makeState(emptyList())
    }

    @Test
    fun `online player unit is skipped`() {
        val playerUnit = makeUnit(1L, 100L, BattleSide.ATTACKER, lastAIEvalTick = 0)
        // Enemy lastAIEvalTick=10 so it won't be due for evaluation at currentTick=10
        val enemy = makeUnit(2L, 200L, BattleSide.DEFENDER, lastAIEvalTick = 10, posX = 800.0)
        state = makeState(
            units = listOf(playerUnit, enemy),
            currentTick = 10,
            connectedPlayers = setOf(100L),
        )

        TacticalAIRunner.processAITick(state)

        assertTrue(state.commandBuffer.isEmpty(), "Online player should not generate AI commands")
        // Player's lastAIEvalTick should remain unchanged
        assertEquals(0, playerUnit.lastAIEvalTick)
    }

    @Test
    fun `dead unit is skipped`() {
        val deadUnit = makeUnit(1L, 100L, BattleSide.ATTACKER, isAlive = false, lastAIEvalTick = 0)
        // Enemy not due for eval
        val enemy = makeUnit(2L, 200L, BattleSide.DEFENDER, lastAIEvalTick = 10)
        state = makeState(units = listOf(deadUnit, enemy), currentTick = 10)

        TacticalAIRunner.processAITick(state)

        assertTrue(state.commandBuffer.isEmpty(), "Dead unit should not generate AI commands")
    }

    @Test
    fun `retreating unit is skipped`() {
        val retreatingUnit = makeUnit(1L, 100L, BattleSide.ATTACKER, isRetreating = true, lastAIEvalTick = 0)
        // Enemy not due for eval
        val enemy = makeUnit(2L, 200L, BattleSide.DEFENDER, lastAIEvalTick = 10)
        state = makeState(units = listOf(retreatingUnit, enemy), currentTick = 10)

        TacticalAIRunner.processAITick(state)

        assertTrue(state.commandBuffer.isEmpty(), "Retreating unit should not generate AI commands")
    }

    @Test
    fun `interval gate prevents evaluation before 10 ticks`() {
        val unit = makeUnit(1L, 100L, BattleSide.ATTACKER, lastAIEvalTick = 5)
        // Enemy not due for eval
        val enemy = makeUnit(2L, 200L, BattleSide.DEFENDER, lastAIEvalTick = 10, posX = 800.0)
        state = makeState(units = listOf(unit, enemy), currentTick = 10)

        TacticalAIRunner.processAITick(state)

        // Only 5 ticks elapsed (10 - 5), need 10 -> should not fire
        assertTrue(state.commandBuffer.isEmpty(), "Should not evaluate when less than 10 ticks elapsed")
        assertEquals(5, unit.lastAIEvalTick, "lastAIEvalTick should not change")
    }

    @Test
    fun `evaluation fires when interval reached`() {
        val unit = makeUnit(1L, 100L, BattleSide.ATTACKER, lastAIEvalTick = 0, posX = 100.0)
        val enemy = makeUnit(2L, 200L, BattleSide.DEFENDER, lastAIEvalTick = 0, posX = 800.0)
        state = makeState(units = listOf(unit, enemy), currentTick = 10)

        TacticalAIRunner.processAITick(state)

        assertFalse(state.commandBuffer.isEmpty(), "Should generate commands when 10 ticks elapsed")
        assertEquals(10, unit.lastAIEvalTick, "lastAIEvalTick should be updated to currentTick")
    }

    @Test
    fun `triggerImmediateReeval resets timer for AI units on specified side`() {
        val unit1 = makeUnit(1L, 100L, BattleSide.ATTACKER, lastAIEvalTick = 8)
        val unit2 = makeUnit(2L, 101L, BattleSide.ATTACKER, lastAIEvalTick = 5)
        val defenderUnit = makeUnit(3L, 200L, BattleSide.DEFENDER, lastAIEvalTick = 7)
        state = makeState(
            units = listOf(unit1, unit2, defenderUnit),
            currentTick = 9,
        )

        TacticalAIRunner.triggerImmediateReeval(state, BattleSide.ATTACKER)

        // Attacker units should be reset
        assertEquals(-TacticalAIRunner.AI_EVAL_INTERVAL, unit1.lastAIEvalTick,
            "Attacker unit1 should have lastAIEvalTick reset")
        assertEquals(-TacticalAIRunner.AI_EVAL_INTERVAL, unit2.lastAIEvalTick,
            "Attacker unit2 should have lastAIEvalTick reset")
        // Defender unit should be unaffected
        assertEquals(7, defenderUnit.lastAIEvalTick,
            "Defender unit should not be affected by attacker reeval")
    }

    @Test
    fun `triggerImmediateReeval skips online player units`() {
        val playerUnit = makeUnit(1L, 100L, BattleSide.ATTACKER, lastAIEvalTick = 8)
        val aiUnit = makeUnit(2L, 101L, BattleSide.ATTACKER, lastAIEvalTick = 5)
        state = makeState(
            units = listOf(playerUnit, aiUnit),
            currentTick = 9,
            connectedPlayers = setOf(100L),
        )

        TacticalAIRunner.triggerImmediateReeval(state, BattleSide.ATTACKER)

        // Player unit should NOT be reset
        assertEquals(8, playerUnit.lastAIEvalTick,
            "Online player unit should not be affected by triggerImmediateReeval")
        // AI unit should be reset
        assertEquals(-TacticalAIRunner.AI_EVAL_INTERVAL, aiUnit.lastAIEvalTick,
            "AI unit should have lastAIEvalTick reset")
    }

    @Test
    fun `after triggerImmediateReeval next processAITick evaluates units`() {
        val unit = makeUnit(1L, 100L, BattleSide.ATTACKER, lastAIEvalTick = 8, posX = 100.0)
        val enemy = makeUnit(2L, 200L, BattleSide.DEFENDER, lastAIEvalTick = 0, posX = 800.0)
        state = makeState(units = listOf(unit, enemy), currentTick = 9)

        // Without reeval, this should NOT fire (9 - 8 = 1 tick, need 10)
        TacticalAIRunner.processAITick(state)
        assertTrue(state.commandBuffer.isEmpty(), "Should not evaluate before reeval trigger")

        // Now trigger immediate reeval
        TacticalAIRunner.triggerImmediateReeval(state, BattleSide.ATTACKER)

        // Process again — now it should fire (9 - (-10) = 19 >= 10)
        TacticalAIRunner.processAITick(state)
        assertFalse(state.commandBuffer.isEmpty(),
            "Should evaluate after triggerImmediateReeval")
        assertEquals(9, unit.lastAIEvalTick,
            "lastAIEvalTick should be updated to currentTick after evaluation")
    }
}
