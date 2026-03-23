package com.opensam.engine.event.actions.economy

import com.opensam.engine.EventActionService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class ProcessWarIncomeAction(
    private val eventActionService: EventActionService,
) : EventAction {
    override val actionType = "process_war_income"

    override fun execute(context: EventActionContext): EventActionResult {
        eventActionService.processWarIncome(context.world)
        return EventActionResult.Success
    }
}
