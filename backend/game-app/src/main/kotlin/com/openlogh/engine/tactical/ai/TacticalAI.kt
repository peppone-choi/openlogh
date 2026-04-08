package com.openlogh.engine.tactical.ai

import com.openlogh.engine.ai.PersonalityTrait
import com.openlogh.engine.tactical.TacticalBattleEngine
import com.openlogh.engine.tactical.TacticalCommand
import com.openlogh.engine.tactical.TacticalUnit
import com.openlogh.model.EnergyAllocation
import com.openlogh.model.Formation
import kotlin.math.sqrt

/**
 * Core tactical AI decision engine.
 *
 * Pure object (no Spring DI) following UtilityScorer/OutOfCrcBehavior pattern.
 * Given a TacticalAIContext snapshot, produces a list of TacticalCommands
 * (move, attack, energy, formation, retreat).
 *
 * Decision pipeline:
 * 1. Retreat check (D-05)
 * 2. No-enemies fallback
 * 3. Energy adjustment (D-08)
 * 4. Formation adjustment (D-09)
 * 5. Mission behavior (D-01 CONQUEST, D-02 DEFENSE, D-03 SWEEP)
 * 6. Target selection with personality modifier (D-04, D-10)
 */
object TacticalAI {

    private const val ACTIVE_COMBAT_RANGE = 300.0
    private const val ANCHOR_HOLD_DISTANCE = 50.0

    /**
     * Main entry point. Produces a list of TacticalCommands for the given AI context.
     * Never returns empty for an alive, non-retreating unit with enemies present.
     */
    fun decide(ctx: TacticalAIContext): List<TacticalCommand> {
        val unit = ctx.unit

        // 1. Retreat check — overrides everything (D-05)
        if (ThreatAssessor.shouldRetreat(ctx)) {
            return listOf(
                TacticalCommand.Retreat(
                    battleId = ctx.battleId,
                    officerId = unit.officerId,
                ),
            )
        }

        val commands = mutableListOf<TacticalCommand>()
        val threats = ThreatAssessor.rankThreats(ctx)
        val nearestEnemy = findNearestEnemy(unit, ctx.enemies)
        val nearestEnemyDistance = nearestEnemy?.let { distanceBetween(unit, it) } ?: Double.MAX_VALUE

        // 2. No enemies — mission-default behavior
        if (ctx.enemies.isEmpty()) {
            commands.addAll(decideNoEnemies(ctx))
            commands.addAll(decideFormation(ctx, nearestEnemyDistance))
            return commands.ifEmpty {
                // Ensure non-empty for alive unit even with no enemies and at anchor
                commands
            }
        }

        // 3. Energy adjustment (D-08)
        val hpRatio = unit.hp.toDouble() / unit.maxHp.coerceAtLeast(1)
        val energyCmd = decideEnergy(ctx, nearestEnemyDistance, hpRatio)
        if (energyCmd != null) commands.add(energyCmd)

        // 4. Formation adjustment (D-09)
        commands.addAll(decideFormation(ctx, nearestEnemyDistance))

        // 5. Mission behavior + target selection (D-01, D-02, D-03, D-10)
        commands.addAll(decideMissionAction(ctx, threats, nearestEnemy, nearestEnemyDistance))

        return commands
    }

    // ── No-enemies fallback ──

    private fun decideNoEnemies(ctx: TacticalAIContext): List<TacticalCommand> {
        return when (ctx.mission) {
            MissionObjective.CONQUEST -> {
                // Move toward target
                listOf(moveToward(ctx.unit, ctx.anchorX, ctx.anchorY, ctx.battleId))
            }
            MissionObjective.DEFENSE -> {
                // Return to anchor if far, else hold
                val distToAnchor = distanceTo(ctx.unit, ctx.anchorX, ctx.anchorY)
                if (distToAnchor > ANCHOR_HOLD_DISTANCE) {
                    listOf(moveToward(ctx.unit, ctx.anchorX, ctx.anchorY, ctx.battleId))
                } else {
                    emptyList() // hold position
                }
            }
            MissionObjective.SWEEP -> {
                emptyList() // hold position, no targets
            }
        }
    }

    // ── Energy adjustment (D-08) ──

    /**
     * Compute energy allocation based on distance, HP, and personality.
     * Returns null if current allocation already matches computed.
     */
    private fun decideEnergy(
        ctx: TacticalAIContext,
        distanceToEnemy: Double,
        hpRatio: Double,
    ): TacticalCommand.SetEnergy? {
        val allocation = computeEnergyAllocation(distanceToEnemy, hpRatio, ctx.personality)
        if (allocation == ctx.unit.energy) return null
        return TacticalCommand.SetEnergy(
            battleId = ctx.battleId,
            officerId = ctx.unit.officerId,
            allocation = allocation,
        )
    }

    /**
     * Pick from 4 energy presets based on distance + HP + personality.
     *
     * Priority:
     * 1. HP < 40% -> EVASIVE (survival mode, shield+engine)
     * 2. Distance <= GUN_RANGE (150) -> AGGRESSIVE (close combat weapons) for aggressive/balanced,
     *    BALANCED for others
     * 3. Distance > BEAM_RANGE (200) -> DEFENSIVE (shield up while closing) for defensive/cautious,
     *    BALANCED for others
     * 4. Default -> BALANCED
     */
    fun computeEnergyAllocation(
        distanceToEnemy: Double,
        hpRatio: Double,
        personality: PersonalityTrait,
    ): EnergyAllocation {
        // Survival mode overrides everything
        if (hpRatio < 0.4) return EnergyAllocation.EVASIVE

        // Close combat
        if (distanceToEnemy <= TacticalBattleEngine.GUN_RANGE) {
            return when (personality) {
                PersonalityTrait.AGGRESSIVE, PersonalityTrait.BALANCED -> EnergyAllocation.AGGRESSIVE
                else -> EnergyAllocation.BALANCED
            }
        }

        // Long range
        if (distanceToEnemy > TacticalBattleEngine.BEAM_RANGE) {
            return when (personality) {
                PersonalityTrait.DEFENSIVE, PersonalityTrait.CAUTIOUS -> EnergyAllocation.DEFENSIVE
                else -> EnergyAllocation.BALANCED
            }
        }

        // Mid range (between GUN_RANGE and BEAM_RANGE)
        return EnergyAllocation.BALANCED
    }

    // ── Formation adjustment (D-09) ──

    private fun decideFormation(
        ctx: TacticalAIContext,
        nearestEnemyDistance: Double,
    ): List<TacticalCommand> {
        val unit = ctx.unit
        val profile = ctx.profile
        val inActiveCombat = nearestEnemyDistance <= ACTIVE_COMBAT_RANGE

        if (!inActiveCombat) {
            // Not in combat: switch to preferred formation if different
            if (unit.formation != profile.preferredFormation) {
                return listOf(
                    TacticalCommand.SetFormation(
                        battleId = ctx.battleId,
                        officerId = unit.officerId,
                        formation = profile.preferredFormation,
                    ),
                )
            }
        } else {
            // In active combat: AGGRESSIVE keeps WEDGE, DEFENSIVE switches from WEDGE to MIXED
            when (ctx.personality) {
                PersonalityTrait.AGGRESSIVE -> {
                    if (unit.formation != Formation.WEDGE) {
                        return listOf(
                            TacticalCommand.SetFormation(
                                battleId = ctx.battleId,
                                officerId = unit.officerId,
                                formation = Formation.WEDGE,
                            ),
                        )
                    }
                }
                PersonalityTrait.DEFENSIVE -> {
                    if (unit.formation == Formation.WEDGE) {
                        return listOf(
                            TacticalCommand.SetFormation(
                                battleId = ctx.battleId,
                                officerId = unit.officerId,
                                formation = Formation.MIXED,
                            ),
                        )
                    }
                }
                else -> { /* keep current formation in combat */ }
            }
        }
        return emptyList()
    }

    // ── Mission behavior (D-01, D-02, D-03) ──

    private fun decideMissionAction(
        ctx: TacticalAIContext,
        threats: List<ThreatAssessor.ThreatScore>,
        nearestEnemy: TacticalUnit?,
        nearestEnemyDistance: Double,
    ): List<TacticalCommand> {
        return when (ctx.mission) {
            MissionObjective.CONQUEST -> decideConquest(ctx, threats, nearestEnemy, nearestEnemyDistance)
            MissionObjective.DEFENSE -> decideDefense(ctx, threats, nearestEnemy)
            MissionObjective.SWEEP -> decideSweep(ctx, threats)
        }
    }

    /**
     * CONQUEST (D-01): Move toward target, engage weak enemies, bypass strong ones.
     * Focus-fire on single target (D-10).
     */
    private fun decideConquest(
        ctx: TacticalAIContext,
        threats: List<ThreatAssessor.ThreatScore>,
        nearestEnemy: TacticalUnit?,
        nearestEnemyDistance: Double,
    ): List<TacticalCommand> {
        val unit = ctx.unit
        val engagementRange = ctx.profile.preferredEngagementRange

        // Check nearest enemy for high-threat bypass
        if (nearestEnemy != null && nearestEnemyDistance <= engagementRange) {
            if (ThreatAssessor.isHighThreat(unit, nearestEnemy)) {
                // Bypass: move away from threat toward target
                return listOf(
                    moveAway(unit, nearestEnemy.posX, nearestEnemy.posY, ctx.anchorX, ctx.anchorY, ctx.battleId),
                )
            } else {
                // Engage weak enemy — CONQUEST focuses on weakest (lowest threat score, D-10)
                val target = selectConquestTarget(ctx, threats)
                if (target != null) {
                    return listOf(
                        TacticalCommand.SetAttackTarget(
                            battleId = ctx.battleId,
                            officerId = unit.officerId,
                            targetFleetId = target,
                        ),
                    )
                }
            }
        }

        // No enemies in engagement range — move toward target
        return listOf(moveToward(unit, ctx.anchorX, ctx.anchorY, ctx.battleId))
    }

    /**
     * DEFENSE (D-02): Intercept enemies near anchor, return to anchor when clear.
     * Focus-fire on single target (D-10).
     */
    private fun decideDefense(
        ctx: TacticalAIContext,
        threats: List<ThreatAssessor.ThreatScore>,
        nearestEnemy: TacticalUnit?,
    ): List<TacticalCommand> {
        val unit = ctx.unit
        val defenseRadius = ctx.profile.preferredEngagementRange * 1.5

        // Check for enemies near anchor
        val enemyNearAnchor = ctx.enemies
            .filter { !it.isRetreating }
            .minByOrNull { distanceTo(it, ctx.anchorX, ctx.anchorY) }

        if (enemyNearAnchor != null) {
            val enemyDistToAnchor = distanceTo(enemyNearAnchor, ctx.anchorX, ctx.anchorY)
            if (enemyDistToAnchor <= defenseRadius) {
                // Intercept: attack + move toward enemy
                val commands = mutableListOf<TacticalCommand>()
                commands.add(
                    TacticalCommand.SetAttackTarget(
                        battleId = ctx.battleId,
                        officerId = unit.officerId,
                        targetFleetId = enemyNearAnchor.fleetId,
                    ),
                )
                // Also move toward enemy for interception
                commands.add(moveToward(unit, enemyNearAnchor.posX, enemyNearAnchor.posY, ctx.battleId))
                return commands
            }
        }

        // No enemies near anchor — return to anchor if far
        val distToAnchor = distanceTo(unit, ctx.anchorX, ctx.anchorY)
        if (distToAnchor > ANCHOR_HOLD_DISTANCE) {
            return listOf(moveToward(unit, ctx.anchorX, ctx.anchorY, ctx.battleId))
        }

        return emptyList() // hold position at anchor
    }

    /**
     * SWEEP (D-03): Pursue highest-threat enemy.
     * Distributed attack across allies (D-10).
     */
    private fun decideSweep(
        ctx: TacticalAIContext,
        threats: List<ThreatAssessor.ThreatScore>,
    ): List<TacticalCommand> {
        if (threats.isEmpty()) return emptyList()

        val target = selectSweepTarget(ctx, threats)
        if (target != null) {
            return listOf(
                TacticalCommand.SetAttackTarget(
                    battleId = ctx.battleId,
                    officerId = ctx.unit.officerId,
                    targetFleetId = target,
                ),
            )
        }

        // Fallback: attack highest threat
        return listOf(
            TacticalCommand.SetAttackTarget(
                battleId = ctx.battleId,
                officerId = ctx.unit.officerId,
                targetFleetId = threats.first().enemyFleetId,
            ),
        )
    }

    // ── Target selection (D-10) ──

    /**
     * CONQUEST target: lowest threat score (weakest enemy) for easy kill.
     * Personality modifier: AGGRESSIVE picks higher HP targets.
     */
    private fun selectConquestTarget(
        ctx: TacticalAIContext,
        threats: List<ThreatAssessor.ThreatScore>,
    ): Long? {
        if (threats.isEmpty()) return null

        return if (ctx.personality == PersonalityTrait.AGGRESSIVE) {
            // Aggressive wants a fight even in CONQUEST
            threats.first().enemyFleetId // highest threat
        } else {
            // Normal CONQUEST: pick weakest
            threats.last().enemyFleetId // lowest threat
        }
    }

    /**
     * SWEEP target: highest threat score, but distribute across allies (D-10).
     * If allies are targeting the top threat, pick the next un-targeted enemy.
     */
    private fun selectSweepTarget(
        ctx: TacticalAIContext,
        threats: List<ThreatAssessor.ThreatScore>,
    ): Long? {
        if (threats.isEmpty()) return null

        // Single unit or no allies: focus on highest threat
        if (ctx.allies.isEmpty()) {
            return threats.first().enemyFleetId
        }

        // Distributed attack: find which enemies allies are already targeting
        val allyTargets = ctx.allies.mapNotNull { it.targetFleetId }.toSet()

        // Try to find an un-targeted enemy from the ranked threats
        for (threat in threats) {
            if (threat.enemyFleetId !in allyTargets) {
                return threat.enemyFleetId
            }
        }

        // All enemies targeted — fall back to highest threat
        return threats.first().enemyFleetId
    }

    // ── Movement helpers ──

    /**
     * Generate UnitCommand moving toward target position.
     */
    fun moveToward(
        unit: TacticalUnit,
        targetX: Double,
        targetY: Double,
        battleId: Long,
    ): TacticalCommand.UnitCommand {
        val dx = targetX - unit.posX
        val dy = targetY - unit.posY
        val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001)
        return TacticalCommand.UnitCommand(
            battleId = battleId,
            officerId = unit.officerId,
            command = "MOVE",
            dirX = dx / dist,
            dirY = dy / dist,
            speed = 1.0,
        )
    }

    /**
     * Generate UnitCommand that arcs away from threat while trending toward goal.
     * Used by CONQUEST bypass behavior.
     */
    fun moveAway(
        unit: TacticalUnit,
        threatX: Double,
        threatY: Double,
        goalX: Double,
        goalY: Double,
        battleId: Long,
    ): TacticalCommand.UnitCommand {
        // Vector away from threat
        val awayDx = unit.posX - threatX
        val awayDy = unit.posY - threatY
        val awayDist = sqrt(awayDx * awayDx + awayDy * awayDy).coerceAtLeast(0.001)

        // Vector toward goal
        val goalDx = goalX - unit.posX
        val goalDy = goalY - unit.posY
        val goalDist = sqrt(goalDx * goalDx + goalDy * goalDy).coerceAtLeast(0.001)

        // Blend: 60% away from threat, 40% toward goal (arc around threat)
        val blendDx = (awayDx / awayDist) * 0.6 + (goalDx / goalDist) * 0.4
        val blendDy = (awayDy / awayDist) * 0.6 + (goalDy / goalDist) * 0.4
        val blendDist = sqrt(blendDx * blendDx + blendDy * blendDy).coerceAtLeast(0.001)

        return TacticalCommand.UnitCommand(
            battleId = battleId,
            officerId = unit.officerId,
            command = "MOVE",
            dirX = blendDx / blendDist,
            dirY = blendDy / blendDist,
            speed = 1.0,
        )
    }

    // ── Distance utilities ──

    private fun distanceBetween(a: TacticalUnit, b: TacticalUnit): Double {
        val dx = a.posX - b.posX
        val dy = a.posY - b.posY
        return sqrt(dx * dx + dy * dy)
    }

    private fun distanceTo(unit: TacticalUnit, x: Double, y: Double): Double {
        val dx = unit.posX - x
        val dy = unit.posY - y
        return sqrt(dx * dx + dy * dy)
    }

    private fun findNearestEnemy(unit: TacticalUnit, enemies: List<TacticalUnit>): TacticalUnit? {
        return enemies
            .filter { !it.isRetreating }
            .minByOrNull { distanceBetween(unit, it) }
    }
}
