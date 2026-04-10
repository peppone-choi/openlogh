package com.openlogh.engine.event.actions.economy

import com.openlogh.engine.EconomyService
import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
import org.springframework.stereotype.Component

/**
 * Legacy parity: hwe/sammo/Event/Action/ProcessIncome.php (upstream a7a19cc3)
 *
 * Triggered by scenario events:
 *   - ["ProcessIncome", "gold"] at month 1 (January)
 *   - ["ProcessIncome", "rice"] at month 7 (July)
 *
 * Reads the resource parameter from the event context (ScenarioService.parseAction
 * converts the legacy `["ProcessIncome", "<resource>"]` form to
 * `params["resource"] = "<resource>"`) and forwards it to EconomyService. Defaults to
 * "gold" when missing so a bare `["ProcessIncome"]` event still wires through the
 * January gold path (matches upstream parity behavior).
 *
 * Resource literal must be "gold" or "rice" — anything else returns Error without
 * invoking EconomyService, mirroring the upstream guard.
 */
@Component
class ProcessIncomeAction(
    private val economyService: EconomyService,
) : EventAction {
    override val actionType = "process_income"

    override fun execute(context: EventActionContext): EventActionResult {
        val resource = (context.params["resource"] as? String) ?: "gold"
        if (resource != "gold" && resource != "rice") {
            return EventActionResult.Error("Invalid resource for ProcessIncome: $resource")
        }
        economyService.processIncomeEvent(context.world, resource)
        return EventActionResult.Success
    }
}
