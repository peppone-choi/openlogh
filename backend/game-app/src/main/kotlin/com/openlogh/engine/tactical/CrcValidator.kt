package com.openlogh.engine.tactical

import com.openlogh.model.CommandRange
import kotlin.math.sqrt

/**
 * Pure-logic CRC (Command Range Circle) validation.
 *
 * D-08: Binary in/out check -- no buffer zone.
 * Pitfall 1: Self-commands ALWAYS bypass CRC.
 *
 * Phase 9 Plan 02: stateless utility, no Spring DI.
 */
object CrcValidator {

    // CRC formula constants (D-05, research)
    const val CRC_BASE_RANGE = 50.0
    const val CRC_RANGE_PER_COMMAND = 3.0
    const val CRC_BASE_EXPANSION = 0.5
    const val CRC_EXPANSION_PER_COMMAND = 0.01  // command / 100.0

    /**
     * Check if target unit is within commander's current CRC.
     * D-08: binary in/out check, no buffer zone.
     */
    fun isWithinCrc(commander: TacticalUnit, target: TacticalUnit): Boolean {
        if (!commander.commandRange.hasCommandRange) return false
        val dx = commander.posX - target.posX
        val dy = commander.posY - target.posY
        val distance = sqrt(dx * dx + dy * dy)
        return commander.commandRange.isInRange(distance)
    }

    /**
     * Check if a command from officerId can reach the target unit.
     * Self-commands (officerId == unit.officerId) ALWAYS bypass CRC (Pitfall 1).
     * Fleet commander commands check fleet commander's CRC.
     * Sub-fleet commander commands check sub-fleet commander's CRC + unit assignment.
     */
    fun isCommandReachable(
        cmd: TacticalCommand,
        targetUnit: TacticalUnit,
        hierarchy: CommandHierarchy,
        allUnits: List<TacticalUnit>,
    ): Boolean {
        // Pitfall 1: self-command always bypasses CRC
        if (cmd.officerId == targetUnit.officerId) return true

        val commandingUnit = allUnits.find { it.officerId == cmd.officerId && it.isAlive }
            ?: return false

        // Fleet commander can command any unit within their CRC
        if (cmd.officerId == hierarchy.fleetCommander) {
            return isWithinCrc(commandingUnit, targetUnit)
        }

        // Sub-fleet commander: must be a registered sub-commander AND target must be assigned to them
        val subFleet = hierarchy.subCommanders[cmd.officerId]
        if (subFleet != null) {
            // Target must be assigned to this sub-fleet commander
            val isAssigned = targetUnit.subFleetCommanderId == cmd.officerId
            if (!isAssigned) return false
            // And target must be within the sub-fleet commander's CRC
            return isWithinCrc(commandingUnit, targetUnit)
        }

        // Unknown officer -- not fleet commander, not sub-fleet commander
        return false
    }

    /**
     * Compute CRC CommandRange for an officer's command stat.
     * Formula (per D-05, research):
     *   maxRange = 50.0 + (command * 3.0)
     *   expansionRate = 0.5 + (command / 100.0)
     */
    fun computeCrcRange(commandStat: Int): CommandRange {
        val maxRange = CRC_BASE_RANGE + (commandStat * CRC_RANGE_PER_COMMAND)
        val expansionRate = CRC_BASE_EXPANSION + (commandStat * CRC_EXPANSION_PER_COMMAND)
        return CommandRange(
            currentRange = 0.0,
            maxRange = maxRange,
            expansionRate = expansionRate,
            hasCommandRange = true,
        )
    }
}
