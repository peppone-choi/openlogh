package com.opensam.engine.event.actions.economy

import com.opensam.engine.EconomyService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
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
