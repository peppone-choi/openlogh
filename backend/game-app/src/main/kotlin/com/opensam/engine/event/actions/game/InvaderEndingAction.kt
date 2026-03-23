package com.opensam.engine.event.actions.game

import com.opensam.engine.EventActionService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class InvaderEndingAction(
    private val eventActionService: EventActionService,
) : EventAction {
    override val actionType = "invader_ending"

    override fun execute(context: EventActionContext): EventActionResult {
        eventActionService.invaderEnding(context.world, context.currentEventId)
        return EventActionResult.Success
    }
}
