package com.openlogh.engine.event.actions.game

import com.openlogh.engine.EventActionService
import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class ResetOfficerLockAction(
    private val eventActionService: EventActionService,
) : EventAction {
    override val actionType = "reset_officer_lock"

    override fun execute(context: EventActionContext): EventActionResult {
        eventActionService.resetOfficerLock(context.world)
        return EventActionResult.Success
    }
}
