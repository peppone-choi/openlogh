package com.openlogh.engine.tactical.ai

import com.openlogh.engine.ai.PersonalityTrait
import com.openlogh.engine.tactical.CommandHierarchy
import com.openlogh.engine.tactical.TacticalUnit

/**
 * Immutable snapshot of the tactical situation for AI decision-making.
 *
 * Constructed each tick for each AI-controlled unit.
 * Pure data class — no side effects, no Spring DI.
 */
data class TacticalAIContext(
    /** The AI-controlled unit making a decision */
    val unit: TacticalUnit,
    /** Alive friendly units (same side, excluding self) */
    val allies: List<TacticalUnit>,
    /** Alive visible enemy units */
    val enemies: List<TacticalUnit>,
    /** Current mission objective */
    val mission: MissionObjective,
    /** Officer personality trait */
    val personality: PersonalityTrait,
    /** Pre-resolved tactical profile for this personality */
    val profile: TacticalTacticsProfile,
    /** Current battle tick */
    val currentTick: Int,
    /** Command hierarchy for this unit's side (nullable if not yet built) */
    val hierarchy: CommandHierarchy? = null,
    /** Defense anchor X (initial position for DEFENSE mission) */
    val anchorX: Double = 0.0,
    /** Defense anchor Y (initial position for DEFENSE mission) */
    val anchorY: Double = 0.0,
    /** Map bounds X */
    val battleBoundsX: Double = 1000.0,
    /** Map bounds Y */
    val battleBoundsY: Double = 600.0,
)
