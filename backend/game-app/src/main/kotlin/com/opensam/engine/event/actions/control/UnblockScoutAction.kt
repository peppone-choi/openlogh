package com.opensam.engine.event.actions.control

import com.opensam.engine.EventActionService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class UnblockScoutAction(
    private val eventActionService: EventActionService,
) : EventAction {
    override val actionType = "unblock_scout_action"

    override fun execute(context: EventActionContext): EventActionResult {
        val blockChangeScout = context.params["blockChangeScout"] as? Boolean
        eventActionService.unblockScoutAction(context.world, blockChangeScout)
        return EventActionResult.Success
    }
}
