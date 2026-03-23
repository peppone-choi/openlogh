package com.opensam.engine.event.actions.economy

import com.opensam.engine.EconomyService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class ProcessIncomeAction(
    private val economyService: EconomyService,
) : EventAction {
    override val actionType = "process_income"

    override fun execute(context: EventActionContext): EventActionResult {
        economyService.processIncomeEvent(context.world)
        return EventActionResult.Success
    }
}
