package com.openlogh.engine.tactical

import com.openlogh.model.InjuryEvent

/**
 * Pure-logic service for successor designation and command delegation (SUCC-01, SUCC-02).
 *
 * No Spring DI, no DB access. Follows CommandHierarchyService/UtilityScorer pattern.
 * All methods operate on in-memory CommandHierarchy data structures.
 *
 * gin7 succession mechanics:
 * - Fleet commander can pre-designate a successor officer (SUCC-01)
 * - Injury severity >= 40 halves command capability (SUCC-02)
 * - Injured commander can voluntarily delegate command to designated successor (SUCC-02)
 * - Command vacancy triggers 30-tick countdown before auto-succession (SUCC-03, future plan)
 */
object SuccessionService {

    /** Injury severity threshold at which command capability is halved */
    const val INJURY_CAPABILITY_THRESHOLD = 40

    /** Capability modifier applied when severely injured */
    const val SEVERE_INJURY_MODIFIER = 0.5

    /** Number of ticks a command vacancy persists before auto-succession (SUCC-03) */
    const val VACANCY_DURATION_TICKS = 30

    /**
     * Designate a successor officer for the fleet (SUCC-01).
     *
     * Only the current active commander (fleet commander or delegated commander) can designate.
     * The successor must be present in the succession queue (a valid officer in the fleet).
     *
     * @param hierarchy the command hierarchy to modify
     * @param commanderId the officer attempting to designate (must be active commander)
     * @param successorId the officer to designate as successor
     * @return null on success, error message string on failure
     */
    fun designateSuccessor(hierarchy: CommandHierarchy, commanderId: Long, successorId: Long): String? {
        val activeCmd = getActiveCommander(hierarchy)
        if (commanderId != activeCmd) {
            return "Only the active commander can designate a successor"
        }
        if (successorId !in hierarchy.successionQueue) {
            return "Successor must be a valid officer in the fleet succession queue"
        }
        hierarchy.designatedSuccessor = successorId
        return null
    }

    /**
     * Apply injury-based capability reduction to the active commander (SUCC-02).
     *
     * If the injured officer is the current active commander and severity >= threshold,
     * the command capability modifier is halved.
     *
     * @param hierarchy the command hierarchy to modify
     * @param injuryEvent the injury event with officer ID and severity
     * @return true if capability was reduced, false otherwise
     */
    fun applyInjuryCapabilityReduction(hierarchy: CommandHierarchy, injuryEvent: InjuryEvent): Boolean {
        val activeCmd = getActiveCommander(hierarchy)
        if (injuryEvent.officerId != activeCmd) {
            return false
        }
        if (injuryEvent.severity >= INJURY_CAPABILITY_THRESHOLD) {
            hierarchy.injuryCapabilityModifier = SEVERE_INJURY_MODIFIER
            return true
        }
        return false
    }

    /**
     * Delegate command from the current active commander to the designated successor (SUCC-02).
     *
     * The commander must be the current active commander, and a successor must be designated.
     *
     * @param hierarchy the command hierarchy to modify
     * @param commanderId the officer delegating command (must be active commander)
     * @return null on success, error message string on failure
     */
    fun delegateCommand(hierarchy: CommandHierarchy, commanderId: Long): String? {
        val activeCmd = getActiveCommander(hierarchy)
        if (commanderId != activeCmd) {
            return "Only the active commander can delegate command"
        }
        if (hierarchy.designatedSuccessor == null) {
            return "No successor designated -- cannot delegate command"
        }
        hierarchy.activeCommander = hierarchy.designatedSuccessor
        hierarchy.commandDelegated = true
        return null
    }

    /**
     * Get the current active commander for the hierarchy.
     *
     * @return activeCommander if set (delegation occurred), otherwise fleetCommander
     */
    fun getActiveCommander(hierarchy: CommandHierarchy): Long {
        return hierarchy.activeCommander ?: hierarchy.fleetCommander
    }

    /**
     * Find the next available successor from the succession queue (SUCC-03).
     *
     * Checks designated successor first, then walks the queue in order.
     * Only officers present in aliveOfficerIds are considered.
     *
     * @return the officer ID of the next successor, or null if none available
     */
    fun findNextSuccessor(hierarchy: CommandHierarchy, aliveOfficerIds: Set<Long>): Long? {
        // Check designated successor first
        hierarchy.designatedSuccessor?.let { designated ->
            if (designated in aliveOfficerIds) return designated
        }
        // Walk succession queue in rank order
        return hierarchy.successionQueue.firstOrNull { it in aliveOfficerIds }
    }

    /**
     * Check if command is completely broken: active commander dead AND no successor available (SUCC-06).
     * This is the terminal state — all units must transition to independent AI.
     *
     * @return true if the active commander is not in aliveOfficerIds AND findNextSuccessor returns null
     */
    fun isCommandBroken(hierarchy: CommandHierarchy, aliveOfficerIds: Set<Long>): Boolean {
        val activeCmd = getActiveCommander(hierarchy)
        // If active commander is still alive, command is not broken
        if (activeCmd in aliveOfficerIds) return false
        // Active commander is dead — check if anyone can succeed
        return findNextSuccessor(hierarchy, aliveOfficerIds) == null
    }
}
