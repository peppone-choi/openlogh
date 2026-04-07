package com.openlogh.engine.tactical

import com.openlogh.model.EnergyAllocation
import com.openlogh.model.Formation
import com.openlogh.model.UnitStance

/**
 * Sealed class representing all tactical battle commands that can be issued via WebSocket.
 *
 * Each subtype maps 1:1 to a @MessageMapping handler in BattleWebSocketController.
 * Used by the command buffer pattern (ENGINE-02): commands are enqueued into
 * ConcurrentLinkedQueue<TacticalCommand> and drained once per tick.
 *
 * Phase 8 Plan 01: data model only (no processing logic).
 * Phase 8 Plan 03: buffer drain integration.
 */
sealed class TacticalCommand {
    abstract val battleId: Long
    abstract val officerId: Long

    /** Energy allocation change (/app/battle/{sessionId}/{battleId}/energy) */
    data class SetEnergy(
        override val battleId: Long,
        override val officerId: Long,
        val allocation: EnergyAllocation,
    ) : TacticalCommand()

    /** Stance change (/app/battle/{sessionId}/{battleId}/stance) */
    data class SetStance(
        override val battleId: Long,
        override val officerId: Long,
        val stance: UnitStance,
    ) : TacticalCommand()

    /** Formation change (extracted from unit-command for type safety) */
    data class SetFormation(
        override val battleId: Long,
        override val officerId: Long,
        val formation: Formation,
    ) : TacticalCommand()

    /** Retreat order (/app/battle/{sessionId}/{battleId}/retreat) */
    data class Retreat(
        override val battleId: Long,
        override val officerId: Long,
    ) : TacticalCommand()

    /** Attack target designation (/app/battle/{sessionId}/{battleId}/attack-target) */
    data class SetAttackTarget(
        override val battleId: Long,
        override val officerId: Long,
        val targetFleetId: Long,
    ) : TacticalCommand()

    /** Tactical unit command 11 types (/app/battle/{sessionId}/{battleId}/unit-command) */
    data class UnitCommand(
        override val battleId: Long,
        override val officerId: Long,
        val command: String,
        val dirX: Double = 0.0,
        val dirY: Double = 0.0,
        val speed: Double = 1.0,
        val targetFleetId: Long? = null,
        val formation: String? = null,
    ) : TacticalCommand()

    /** Planet conquest command (/app/battle/{sessionId}/{battleId}/planet-conquest) */
    data class PlanetConquest(
        override val battleId: Long,
        override val officerId: Long,
        val request: ConquestRequest,
    ) : TacticalCommand()

    /** Sub-fleet assignment command (Phase 9: fleet commander assigns units to sub-commander) */
    data class AssignSubFleet(
        override val battleId: Long,
        override val officerId: Long,  // fleet commander issuing the assignment
        val subCommanderId: Long,
        val unitIds: List<Long>,       // TacticalUnit.fleetId values to assign
    ) : TacticalCommand()

    /** Unit reassignment command (Phase 9: move a unit between sub-fleets or back to fleet commander) */
    data class ReassignUnit(
        override val battleId: Long,
        override val officerId: Long,
        val unitId: Long,
        val newSubCommanderId: Long?,  // null = return to fleet commander direct control
    ) : TacticalCommand()
}
