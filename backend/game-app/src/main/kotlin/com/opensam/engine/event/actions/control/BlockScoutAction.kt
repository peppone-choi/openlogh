package com.opensam.engine.event.actions.control

import com.opensam.engine.EventActionService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class BlockScoutAction(
    private val eventActionService: EventActionService,
) : EventAction {
    override val actionType = "block_scout_action"

    override fun execute(context: EventActionContext): EventActionResult {
        val blockChangeScout = context.params["blockChangeScout"] as? Boolean
        eventActionService.blockScoutAction(context.world, blockChangeScout)
        return EventActionResult.Success
    }
}
