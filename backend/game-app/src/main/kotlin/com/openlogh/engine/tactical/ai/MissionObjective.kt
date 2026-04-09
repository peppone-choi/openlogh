package com.openlogh.engine.tactical.ai

import com.openlogh.engine.ai.PersonalityTrait

/**
 * Mission objective for tactical AI behavior.
 * Determines the primary goal an AI-controlled unit pursues during tactical combat.
 *
 * - CONQUEST: Capture territory, engage weak targets, bypass strong ones
 * - DEFENSE: Hold position near anchor point, repel attackers
 * - SWEEP: Eliminate all enemy units systematically
 */
enum class MissionObjective(val korean: String) {
    CONQUEST("점령"),
    DEFENSE("방어"),
    SWEEP("소탕"),
    ;

    companion object {
        /**
         * Phase 12 D-10: Default MissionObjective for fleets not participating in any OperationPlan.
         * AGGRESSIVE personalities sweep; all others default to DEFENSE.
         */
        fun defaultForPersonality(personality: PersonalityTrait): MissionObjective = when (personality) {
            PersonalityTrait.AGGRESSIVE -> SWEEP
            PersonalityTrait.DEFENSIVE -> DEFENSE
            PersonalityTrait.CAUTIOUS -> DEFENSE
            PersonalityTrait.BALANCED -> DEFENSE
            PersonalityTrait.POLITICAL -> DEFENSE
        }
    }
}
