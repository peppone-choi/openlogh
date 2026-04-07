package com.openlogh.engine.tactical

/**
 * Pure-logic service for sub-fleet assignment and command delegation (CMD-01, CMD-02).
 *
 * No Spring DI, no DB access. Follows Phase 5 UtilityScorer pattern.
 * All methods operate on in-memory tactical battle data structures.
 *
 * gin7 organizational structure:
 * - Fleet (max 60 units) divided among commander + sub-commanders
 * - Sub-commanders must be crew officers (CrewSlotRole holders)
 * - Only fleet commander can assign/reassign units
 * - Priority ordering: online > rank > evaluation > merit > officerId
 */
object CommandHierarchyService {

    /** Maximum total units that can be assigned across all sub-fleets in a hierarchy */
    const val MAX_TOTAL_ASSIGNED_UNITS = 60

    /**
     * Validate a sub-fleet assignment request.
     *
     * @return null if valid, error message string if invalid
     */
    fun validateSubFleetAssignment(
        hierarchy: CommandHierarchy,
        commanderId: Long,
        subCommanderId: Long,
        unitIds: List<Long>,
        crewOfficerIds: Set<Long>,
    ): String? {
        // Only fleet commander can assign sub-fleets
        if (commanderId != hierarchy.fleetCommander) {
            return "Only the fleet commander can assign sub-fleets"
        }
        // Sub-commander must be a crew officer (D-04)
        if (subCommanderId !in crewOfficerIds) {
            return "Sub-commander must be a crew officer in the fleet"
        }
        // Unit list must not be empty (D-02)
        if (unitIds.isEmpty()) {
            return "Unit list must not be empty"
        }
        // Total assigned units across all sub-fleets cannot exceed 60
        // Count existing assignments, excluding units that will be reassigned to this new sub-fleet
        val currentTotal = hierarchy.subCommanders.values.sumOf { subFleet ->
            subFleet.unitIds.count { it !in unitIds }
        }
        if (currentTotal + unitIds.size > MAX_TOTAL_ASSIGNED_UNITS) {
            return "Total assigned units cannot exceed $MAX_TOTAL_ASSIGNED_UNITS (current: $currentTotal, requested: ${unitIds.size})"
        }
        return null
    }

    /**
     * Assign units to a sub-fleet commander.
     *
     * Mutates hierarchy.subCommanders and each assigned unit's subFleetCommanderId.
     * Units already in other sub-fleets are automatically removed from old assignments.
     */
    fun assignSubFleet(
        hierarchy: CommandHierarchy,
        subCommanderId: Long,
        subCommanderName: String,
        subCommanderRank: Int,
        unitIds: List<Long>,
        allUnits: List<TacticalUnit>,
    ) {
        val unitIdSet = unitIds.toSet()

        // Step 1: Remove these unitIds from any existing sub-fleet
        for ((officerId, subFleet) in hierarchy.subCommanders) {
            val remaining = subFleet.unitIds.filter { it !in unitIdSet }
            if (remaining.size != subFleet.unitIds.size) {
                hierarchy.subCommanders[officerId] = subFleet.copy(unitIds = remaining)
            }
        }

        // Step 2: Create/update the sub-fleet entry
        hierarchy.subCommanders[subCommanderId] = SubFleet(
            commanderId = subCommanderId,
            commanderName = subCommanderName,
            unitIds = unitIds,
            commanderRank = subCommanderRank,
        )

        // Step 3: Update TacticalUnit.subFleetCommanderId for assigned units
        for (unit in allUnits) {
            if (unit.fleetId in unitIdSet) {
                unit.subFleetCommanderId = subCommanderId
            }
        }
    }

    /**
     * Resolve which officer commands a given unit.
     *
     * @return sub-fleet commander's officerId if assigned, otherwise fleet commander's officerId
     */
    fun resolveCommanderForUnit(
        unit: TacticalUnit,
        hierarchy: CommandHierarchy,
    ): Long {
        return unit.subFleetCommanderId ?: hierarchy.fleetCommander
    }

    /**
     * Build a priority-sorted list from officer data and online status.
     *
     * @return list sorted by priority descending (highest priority first)
     */
    fun buildPriorityList(
        officerData: List<OfficerPriorityData>,
        onlineOfficerIds: Set<Long>,
    ): List<CommandPriority> {
        return officerData.map { data ->
            CommandPriority(
                isOnline = data.officerId in onlineOfficerIds,
                rank = data.rank,
                evaluation = data.evaluation,
                merit = data.merit,
                officerId = data.officerId,
            )
        }.sortedDescending()
    }
}

/**
 * Officer data for priority calculation. Lightweight DTO to avoid
 * depending on the full Officer entity in pure-logic code.
 */
data class OfficerPriorityData(
    val officerId: Long,
    val rank: Int,
    val evaluation: Int,
    val merit: Int,
)
