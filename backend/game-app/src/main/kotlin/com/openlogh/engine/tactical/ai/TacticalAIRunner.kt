package com.openlogh.engine.tactical.ai

import com.openlogh.engine.tactical.BattleSide
import com.openlogh.engine.tactical.TacticalBattleState
import com.openlogh.engine.tactical.TacticalUnit

/**
 * AI tick processor that builds TacticalAIContext snapshots for NPC/offline units
 * and invokes TacticalAI.decide() to generate commands.
 *
 * Pure object (no Spring DI) following UtilityScorer/OutOfCrcBehavior pattern.
 *
 * D-06: AI units are re-evaluated every 10 ticks.
 * D-07: Flagship destruction or command breakdown triggers immediate re-evaluation.
 * Online player-controlled units are never processed by AI.
 */
object TacticalAIRunner {
    /** D-06: re-evaluation interval in ticks */
    const val AI_EVAL_INTERVAL = 10

    /**
     * Process AI decisions for all eligible NPC/offline units.
     * Called at step 0.7 of processTick (after CRC, before movement).
     *
     * Skips:
     * - Dead units
     * - Retreating units
     * - Online player-controlled units (officerId in connectedPlayerOfficerIds)
     * - Units evaluated less than AI_EVAL_INTERVAL ticks ago
     */
    fun processAITick(state: TacticalBattleState) {
        for (unit in state.units) {
            if (!unit.isAlive || unit.isRetreating) continue
            if (unit.officerId in state.connectedPlayerOfficerIds) continue

            // D-06: Regular re-evaluation every 10 ticks
            if (state.currentTick - unit.lastAIEvalTick < AI_EVAL_INTERVAL) continue

            val commands = evaluateUnit(unit, state)
            for (cmd in commands) {
                state.commandBuffer.add(cmd)
            }
            unit.lastAIEvalTick = state.currentTick
        }
    }

    /**
     * D-07: Force immediate re-evaluation for all AI units on a side.
     * Called when flagship is destroyed or command breakdown occurs.
     * Resets lastAIEvalTick so the next processAITick will evaluate them.
     *
     * Online player-controlled units are not affected.
     */
    fun triggerImmediateReeval(state: TacticalBattleState, side: BattleSide) {
        for (unit in state.units) {
            if (!unit.isAlive || unit.side != side) continue
            if (unit.officerId in state.connectedPlayerOfficerIds) continue
            unit.lastAIEvalTick = -AI_EVAL_INTERVAL  // Force next tick eval
        }
    }

    /**
     * Build TacticalAIContext for a unit and invoke TacticalAI.decide().
     */
    private fun evaluateUnit(unit: TacticalUnit, state: TacticalBattleState): List<com.openlogh.engine.tactical.TacticalCommand> {
        val side = unit.side
        val allies = state.units.filter { it.isAlive && it.side == side && it.fleetId != unit.fleetId }
        val enemies = state.units.filter { it.isAlive && it.side != side && !it.isRetreating }
        val hierarchy = if (side == BattleSide.ATTACKER) state.attackerHierarchy else state.defenderHierarchy
        val profile = TacticalPersonalityConfig.forTrait(unit.personality)

        val ctx = TacticalAIContext(
            battleId = state.battleId,
            unit = unit,
            allies = allies,
            enemies = enemies,
            mission = unit.missionObjective,
            personality = unit.personality,
            profile = profile,
            currentTick = state.currentTick,
            hierarchy = hierarchy,
            anchorX = unit.anchorX,
            anchorY = unit.anchorY,
            battleBoundsX = state.battleBoundsX,
            battleBoundsY = state.battleBoundsY,
        )
        return TacticalAI.decide(ctx)
    }
}
