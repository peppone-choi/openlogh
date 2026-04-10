package com.openlogh.engine.event.actions.economy

import com.openlogh.engine.EconomyService
import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
import org.springframework.stereotype.Component

/**
 * Legacy parity: hwe/sammo/Event/Action/ProcessSemiAnnual.php (upstream a7a19cc3)
 *
 * Triggered by scenario events:
 *   - ["ProcessSemiAnnual", "gold"] at month 1 (January) → decays funds maintenance
 *   - ["ProcessSemiAnnual", "rice"] at month 7 (July)    → decays supplies maintenance
 *
 * Reads the resource parameter from the event context (ScenarioService.parseAction
 * converts the legacy `["ProcessSemiAnnual", "<resource>"]` form to
 * `params["resource"] = "<resource>"`) and forwards it to EconomyService. Defaults to
 * "gold" when missing so a bare `["ProcessSemiAnnual"]` event still wires through
 * the January funds-decay path (matches upstream parity behavior).
 *
 * Resource literal must be "gold" or "rice" — anything else returns Error without
 * invoking EconomyService, mirroring the upstream guard.
 */
@Component
class ProcessSemiAnnualAction(
    private val economyService: EconomyService,
) : EventAction {
    override val actionType = "process_semi_annual"

    override fun execute(context: EventActionContext): EventActionResult {
        val resource = (context.params["resource"] as? String) ?: "gold"
        if (resource != "gold" && resource != "rice") {
            return EventActionResult.Error("Invalid resource for ProcessSemiAnnual: $resource")
        }
        economyService.processSemiAnnualEvent(context.world, resource)
        return EventActionResult.Success
    }
}
