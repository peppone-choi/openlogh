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
    /** Remaining ticks of communication jamming (0 = not jammed, Plan 04 integration) */
    var jammingTicksRemaining: Int = 0,
    /** Officer ID that caused the current jamming (null = no active jammer) */
    var jammingSourceOfficerId: Long? = null,
    /** Designated successor officer ID (SUCC-01: pre-designated by fleet commander) */
    var designatedSuccessor: Long? = null,
    /** Injury-based capability modifier: 1.0 = full, 0.5 = reduced (SUCC-02) */
    var injuryCapabilityModifier: Double = 1.0,
    /** Tick when command vacancy started (-1 = no vacancy) (SUCC-03: 30-tick countdown) */
    var vacancyStartTick: Int = -1,
    /** Whether command has been delegated to successor (SUCC-02: voluntary delegation) */
    var commandDelegated: Boolean = false,
    /** Officer ID who received delegated command (tracks active commander after delegation) */
    var activeCommander: Long? = null,
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
    /** TacticalUnit IDs (fleetId) assigned to this sub-fleet (per D-01: individual ShipUnit assignment) */
    val unitIds: List<Long>,
    /** Commander's rank level (0-10, used for succession ordering) */
    val commanderRank: Int,
)
