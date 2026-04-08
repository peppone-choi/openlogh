package com.openlogh.engine.tactical.ai

import com.openlogh.engine.tactical.TacticalUnit
import kotlin.math.sqrt

/**
 * Threat scoring and retreat condition logic for tactical AI.
 *
 * Pure object (no Spring DI), following UtilityScorer/OutOfCrcBehavior pattern.
 * Scores enemy units by HP ratio, proximity, ship count, and attack stat.
 */
object ThreatAssessor {

    private const val HIGH_THREAT_THRESHOLD = 60.0
    private const val MAX_SCORING_DISTANCE = 1000.0

    /**
     * Scored threat entry for an enemy unit.
     */
    data class ThreatScore(val enemyFleetId: Long, val score: Double)

    /**
     * Score how dangerous an enemy unit is to self.
     *
     * Formula (0-100 scale):
     *   HP ratio component:   (enemy.hp / enemy.maxHp) * 40
     *   Ship ratio component: (enemy.ships / enemy.maxShips) * 20
     *   Proximity component:  (1.0 - distance / 1000.0) * 25  (clamped to 0)
     *   Attack component:     (enemy.attack / 100.0) * 15
     *
     * Higher score = more dangerous.
     */
    fun scoreThreat(self: TacticalUnit, enemy: TacticalUnit): Double {
        val dx = self.posX - enemy.posX
        val dy = self.posY - enemy.posY
        val distance = sqrt(dx * dx + dy * dy)

        val hpRatio = enemy.hp.toDouble() / enemy.maxHp.coerceAtLeast(1)
        val shipRatio = enemy.ships.toDouble() / enemy.maxShips.coerceAtLeast(1)
        val proximityFactor = (1.0 - distance / MAX_SCORING_DISTANCE).coerceAtLeast(0.0)
        val attackFactor = enemy.attack.toDouble() / 100.0

        return hpRatio * 40.0 + shipRatio * 20.0 + proximityFactor * 25.0 + attackFactor * 15.0
    }

    /**
     * Rank all visible enemies by threat score, descending (most dangerous first).
     * Filters out retreating enemies since they are no longer combat-active.
     */
    fun rankThreats(ctx: TacticalAIContext): List<ThreatScore> {
        return ctx.enemies
            .filter { !it.isRetreating }
            .map { enemy -> ThreatScore(enemy.fleetId, scoreThreat(ctx.unit, enemy)) }
            .sortedByDescending { it.score }
    }

    /**
     * Check whether this AI unit should retreat based on personality-specific thresholds.
     *
     * Retreat triggers (OR logic):
     * - HP ratio below personality retreatHpThreshold (D-05)
     * - Morale below personality retreatMoraleThreshold
     */
    fun shouldRetreat(ctx: TacticalAIContext): Boolean {
        val hpRatio = ctx.unit.hp.toDouble() / ctx.unit.maxHp.coerceAtLeast(1)
        return hpRatio < ctx.profile.retreatHpThreshold ||
            ctx.unit.morale < ctx.profile.retreatMoraleThreshold
    }

    /**
     * Check if an enemy is a high threat (score > 60).
     * Used by CONQUEST AI to decide "engage weak, bypass strong" per D-01.
     */
    fun isHighThreat(self: TacticalUnit, enemy: TacticalUnit): Boolean {
        return scoreThreat(self, enemy) > HIGH_THREAT_THRESHOLD
    }
}
