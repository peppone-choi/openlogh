package com.opensam.engine.event.actions.game

import com.opensam.engine.EventActionService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
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
