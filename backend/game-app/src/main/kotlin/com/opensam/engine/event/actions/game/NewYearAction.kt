package com.opensam.engine.event.actions.game

import com.opensam.engine.EventActionService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class NewYearAction(
    private val eventActionService: EventActionService,
) : EventAction {
    override val actionType = "new_year"

    override fun execute(context: EventActionContext): EventActionResult {
        eventActionService.newYear(context.world)
        return EventActionResult.Success
    }
}
