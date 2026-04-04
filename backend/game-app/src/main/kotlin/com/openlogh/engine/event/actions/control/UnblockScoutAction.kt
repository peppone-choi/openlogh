package com.openlogh.engine.event.actions.control

import com.openlogh.engine.EventActionService
import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
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
