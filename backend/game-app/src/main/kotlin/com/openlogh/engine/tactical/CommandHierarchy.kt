package com.openlogh.engine.tactical

/**
 * Data model for command hierarchy within a tactical battle fleet.
 *
 * Represents the chain of command for gin7's organizational simulation:
 * - Fleet commander issues orders to sub-fleet commanders
 * - Command Range Circle (CRC) determines which units receive orders
 * - Succession queue handles flagship destruction -> command transfer
 * - Communication jamming blocks order propagation
 *
 * Phase 8 Plan 01: pure data model (no logic methods).
 * Phase 8 Plan 03: initialization via buildCommandHierarchy().
 * Phase 9-10: full hierarchy processing with CRC and succession.
 */
data class CommandHierarchy(
    /** Fleet commander officer ID */
    val fleetCommander: Long,
    /** Officer ID -> SubFleet mapping for sub-fleet commanders */
    val subCommanders: MutableMap<Long, SubFleet> = mutableMapOf(),
    /** Ordered by rank descending -- next in line for command succession */
    val successionQueue: MutableList<Long> = mutableListOf(),
    /** Officer ID -> Command Range Circle radius (in tactical map units) */
    val crcRadius: MutableMap<Long, Double> = mutableMapOf(),
    /** Communication jammed flag -- blocks order propagation when true */
    var commJammed: Boolean = false,
)

/**
 * Sub-fleet within a command hierarchy.
 *
 * Represents a subordinate commander's assigned units within the fleet.
 * Maps to gin7's organizational structure where a fleet (max 60 units)
 * is divided among a commander, vice-commander, chief of staff, and staff officers.
 */
data class SubFleet(
    /** Sub-fleet commander officer ID */
    val commanderId: Long,
    /** Sub-fleet commander display name */
    val commanderName: String,
    /** Fleet IDs assigned to this sub-fleet */
    val unitFleetIds: List<Long>,
    /** Commander's rank level (0-10, used for succession ordering) */
    val commanderRank: Int,
)
