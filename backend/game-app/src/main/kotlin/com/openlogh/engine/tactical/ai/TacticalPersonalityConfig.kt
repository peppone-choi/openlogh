package com.openlogh.engine.tactical.ai

import com.openlogh.engine.ai.PersonalityTrait
import com.openlogh.model.Formation

/**
 * Per-personality tactical parameters for AI decision-making.
 *
 * Pure object (no Spring DI), following UtilityScorer/OutOfCrcBehavior pattern.
 * Values derived from design decisions D-05 (retreat thresholds) and D-09 (formation preferences).
 */
data class TacticalTacticsProfile(
    /** HP ratio below which AI retreats (D-05) */
    val retreatHpThreshold: Double,
    /** Morale below which AI retreats */
    val retreatMoraleThreshold: Int,
    /** Preferred combat distance (lower = closer engagement) */
    val preferredEngagementRange: Double,
    /** Preferred formation (D-09) */
    val preferredFormation: Formation,
    /** 0.0-1.0 scale for target selection bias (higher = more aggressive) */
    val aggressionFactor: Double,
)

/**
 * Maps PersonalityTrait to tactical behavior profiles.
 *
 * Retreat thresholds (D-05):
 * - AGGRESSIVE: 10% HP (fights to near-death)
 * - CAUTIOUS: 30% HP (retreats early)
 * - DEFENSIVE/BALANCED/POLITICAL: 20% HP (moderate)
 *
 * Formation preferences (D-09):
 * - AGGRESSIVE -> WEDGE (紡錘, maximum offensive punch)
 * - DEFENSIVE -> MIXED (混成, balanced defense)
 * - CAUTIOUS -> BY_CLASS (艦種, organized by ship type)
 * - BALANCED -> MIXED (混成, general purpose)
 * - POLITICAL -> THREE_COLUMN (三列, conservative formation)
 */
object TacticalPersonalityConfig {

    fun forTrait(trait: PersonalityTrait): TacticalTacticsProfile = when (trait) {
        PersonalityTrait.AGGRESSIVE -> TacticalTacticsProfile(
            retreatHpThreshold = 0.10,
            retreatMoraleThreshold = 15,
            preferredEngagementRange = 120.0,
            preferredFormation = Formation.WEDGE,
            aggressionFactor = 0.9,
        )
        PersonalityTrait.DEFENSIVE -> TacticalTacticsProfile(
            retreatHpThreshold = 0.20,
            retreatMoraleThreshold = 30,
            preferredEngagementRange = 250.0,
            preferredFormation = Formation.MIXED,
            aggressionFactor = 0.3,
        )
        PersonalityTrait.BALANCED -> TacticalTacticsProfile(
            retreatHpThreshold = 0.20,
            retreatMoraleThreshold = 30,
            preferredEngagementRange = 180.0,
            preferredFormation = Formation.MIXED,
            aggressionFactor = 0.5,
        )
        PersonalityTrait.POLITICAL -> TacticalTacticsProfile(
            retreatHpThreshold = 0.20,
            retreatMoraleThreshold = 30,
            preferredEngagementRange = 200.0,
            preferredFormation = Formation.THREE_COLUMN,
            aggressionFactor = 0.4,
        )
        PersonalityTrait.CAUTIOUS -> TacticalTacticsProfile(
            retreatHpThreshold = 0.30,
            retreatMoraleThreshold = 40,
            preferredEngagementRange = 300.0,
            preferredFormation = Formation.BY_CLASS,
            aggressionFactor = 0.2,
        )
    }
}
