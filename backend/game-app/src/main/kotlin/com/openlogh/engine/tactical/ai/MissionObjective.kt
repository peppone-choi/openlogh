package com.openlogh.engine.tactical.ai

/**
 * Mission objective for tactical AI behavior.
 * Determines the primary goal an AI-controlled unit pursues during tactical combat.
 *
 * - CONQUEST: Capture territory, engage weak targets, bypass strong ones
 * - DEFENSE: Hold position near anchor point, repel attackers
 * - SWEEP: Eliminate all enemy units systematically
 *
 * Stub for Phase 12 connection — AI receives this as a parameter from operation plan,
 * not from DB directly.
 */
enum class MissionObjective(val korean: String) {
    CONQUEST("점령"),
    DEFENSE("방어"),
    SWEEP("소탕"),
}
