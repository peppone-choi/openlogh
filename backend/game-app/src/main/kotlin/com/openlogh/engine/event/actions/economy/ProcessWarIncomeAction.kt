package com.openlogh.engine.event.actions.economy

import com.openlogh.engine.EventActionService
import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
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
