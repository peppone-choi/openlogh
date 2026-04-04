package com.openlogh.engine.event.actions.game

import com.openlogh.engine.EventActionService
import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
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
