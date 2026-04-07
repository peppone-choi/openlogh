package com.openlogh.engine.tactical

import kotlin.math.sqrt

/**
 * Out-of-CRC unit behavior: maintain last order + HP<30% AI retreat.
 *
 * D-06: Units outside CRC maintain their last order (velocity unchanged).
 * If HP drops below 30%, AI autonomous retreat is triggered.
 * Pitfall 5: Units stuck outside CRC for too long move toward commander.
 *
 * Phase 9 Plan 02: stateless utility, no Spring DI.
 */
object OutOfCrcBehavior {
    const val HP_RETREAT_THRESHOLD = 0.3
    const val RETREAT_SPEED_FACTOR = 0.8
    const val MAX_AUTONOMOUS_TICKS = 120  // ~2 minutes at 1 tick/sec
    const val BATTLE_BOUNDS_X = 1000.0
    private const val MOVE_TOWARD_COMMANDER_SPEED_FACTOR = 0.5

    /**
     * Process behavior for a unit that is outside its commander's CRC.
     * D-06: maintain last order + HP<30% AI retreat.
     *
     * Priority:
     * 1. Dead units are skipped
     * 2. HP < 30% and not retreating -> AI retreat toward own edge
     * 3. Stuck for > MAX_AUTONOMOUS_TICKS and commander available -> move toward commander (Pitfall 5)
     * 4. Else: maintain last order (velocity unchanged)
     *
     * @param unit The out-of-CRC unit
     * @param commanderUnit The commanding officer's TacticalUnit (for move-toward fallback)
     * @param currentTick Current battle tick number
     */
    fun processOutOfCrcUnit(
        unit: TacticalUnit,
        commanderUnit: TacticalUnit?,
        currentTick: Int,
    ) {
        // 1. Skip dead units
        if (!unit.isAlive) return

        // 2. HP < 30% retreat check
        val hpRatio = unit.hp.toDouble() / unit.maxHp.coerceAtLeast(1)
        if (hpRatio < HP_RETREAT_THRESHOLD && !unit.isRetreating) {
            unit.isRetreating = true
            // Retreat direction: ATTACKER toward posX=0 (left), DEFENDER toward posX=BATTLE_BOUNDS_X (right)
            val retreatDirection = if (unit.side == BattleSide.ATTACKER) -1.0 else 1.0
            unit.velX = retreatDirection * TacticalBattleEngine.BASE_SPEED * RETREAT_SPEED_FACTOR
            unit.velY = 0.0
            return
        }

        // 3. Pitfall 5 avoidance: stuck units move toward commander after MAX_AUTONOMOUS_TICKS
        if (commanderUnit != null && (currentTick - unit.lastCommandTick) > MAX_AUTONOMOUS_TICKS) {
            val dx = commanderUnit.posX - unit.posX
            val dy = commanderUnit.posY - unit.posY
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001)
            val speed = TacticalBattleEngine.BASE_SPEED * MOVE_TOWARD_COMMANDER_SPEED_FACTOR
            unit.velX = (dx / dist) * speed
            unit.velY = (dy / dist) * speed
            return
        }

        // 4. Default: maintain last order (velocity unchanged)
    }
}
