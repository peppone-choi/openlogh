package com.openlogh.engine.event.actions.economy

import com.openlogh.engine.EconomyService
import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class RaiseDisasterAction(
    private val economyService: EconomyService,
) : EventAction {
    override val actionType = "raise_disaster"

    override fun execute(context: EventActionContext): EventActionResult {
        economyService.processDisasterOrBoom(context.world)
        return EventActionResult.Success
    }
}
