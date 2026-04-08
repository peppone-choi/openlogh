package com.openlogh.engine.tactical

/**
 * Communication jamming system for tactical battles.
 *
 * gin7 D-12: Enemy special ability/equipment triggers communication jamming.
 * D-13: Only fleet commander's fleet-wide orders are blocked; sub-fleet commanders can still
 *        command their own units. Self-commands are never blocked.
 * D-14: Jamming auto-clears after tick countdown expires, or immediately if the
 *        jamming source officer is destroyed/retreated.
 *
 * Pure object -- no Spring DI, operates only on CommandHierarchy data.
 */
object CommunicationJamming {
    const val JAMMING_DEFAULT_DURATION_TICKS = 60  // ~1 minute at 1 tick/sec

    /**
     * Apply communication jamming to a fleet's hierarchy.
     * D-12: triggered by enemy special ability/equipment.
     */
    fun applyJamming(
        hierarchy: CommandHierarchy,
        sourceOfficerId: Long,
        durationTicks: Int = JAMMING_DEFAULT_DURATION_TICKS,
    ) {
        hierarchy.commJammed = true
        hierarchy.jammingTicksRemaining = durationTicks
        hierarchy.jammingSourceOfficerId = sourceOfficerId
    }

    /**
     * Check if a fleet-wide command is blocked by jamming.
     * D-13: only fleet commander's fleet-wide orders are blocked.
     * Sub-fleet commander -> own units: NOT blocked.
     * Self-commands: NOT blocked.
     */
    fun isFleetWideCommandBlocked(
        cmd: TacticalCommand,
        targetUnit: TacticalUnit,
        hierarchy: CommandHierarchy,
    ): Boolean {
        if (!hierarchy.commJammed) return false
        // Self-command: never blocked
        if (cmd.officerId == targetUnit.officerId) return false
        // Only fleet commander's commands are blocked
        if (cmd.officerId != hierarchy.fleetCommander) return false
        // Fleet commander is jammed: fleet-wide orders blocked
        return true
    }

    /**
     * Tick countdown for jamming duration.
     * D-14: auto-clear after ticks expire.
     */
    fun tickJamming(hierarchy: CommandHierarchy) {
        if (!hierarchy.commJammed) return
        hierarchy.jammingTicksRemaining--
        if (hierarchy.jammingTicksRemaining <= 0) {
            clearJamming(hierarchy)
        }
    }

    /**
     * Clear jamming if the source officer is dead or retreated.
     * D-14: jammer destroyed/retreated -> immediate clear.
     */
    fun clearJammingIfSourceGone(
        hierarchy: CommandHierarchy,
        allUnits: List<TacticalUnit>,
    ) {
        val sourceId = hierarchy.jammingSourceOfficerId ?: return
        val sourceUnit = allUnits.find { it.officerId == sourceId }
        if (sourceUnit == null || !sourceUnit.isAlive || sourceUnit.isRetreating) {
            clearJamming(hierarchy)
        }
    }

    private fun clearJamming(hierarchy: CommandHierarchy) {
        hierarchy.commJammed = false
        hierarchy.jammingTicksRemaining = 0
        hierarchy.jammingSourceOfficerId = null
    }
}
