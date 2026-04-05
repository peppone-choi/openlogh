package com.openlogh.engine.turn.steps

import com.openlogh.engine.SovereignConstants
import com.openlogh.engine.OfficerMaintenanceService
import com.openlogh.engine.SpecialAssignmentService
import com.openlogh.engine.turn.TurnContext
import com.openlogh.engine.turn.TurnStep
import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.engine.turn.cqrs.persist.toEntity
import com.openlogh.engine.turn.cqrs.persist.toSnapshot
import com.openlogh.service.InheritanceService
import org.springframework.stereotype.Component

/**
 * Step 1500: Officer maintenance — age, experience, dedication, injury, retirement,
 * special assignments, and monthly inheritance accrual.
 *
 * Legacy: processGeneralMaintenance + checkAndAssignSpecials + monthly inheritance.
 */
@Component
class OfficerMaintenanceStep(
    private val officerMaintenanceService: OfficerMaintenanceService,
    private val specialAssignmentService: SpecialAssignmentService,
    private val inheritanceService: InheritanceService,
    private val worldPortFactory: JpaWorldPortFactory,
) : TurnStep {
    override val name = "GeneralMaintenance"
    override val order = 1500

    override fun execute(context: TurnContext) {
        val ports = worldPortFactory.create(context.sessionId)
        val beforeSnapshots = ports.allOfficers().associateBy { it.id }
        val generals = beforeSnapshots.values.map { it.toEntity() }

        officerMaintenanceService.processGeneralMaintenance(context.world, generals)
        specialAssignmentService.checkAndAssignSpecials(context.world, generals)

        for (g in generals) {
            val snap = g.toSnapshot()
            if (snap != beforeSnapshots[g.id]) ports.putOfficer(snap)
        }

        // Monthly inheritance accrual for non-NPC, non-emperor generals
        for (general in generals) {
            if (general.npcState.toInt() != 5 && general.npcState != SovereignConstants.NPC_STATE_EMPEROR) {
                inheritanceService.accruePoints(general, "lived_month", 1)
            }
        }
    }
}
