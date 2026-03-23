package com.opensam.engine.event.actions.game

import com.opensam.engine.EventActionService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class LostUniqueItemAction(
    private val eventActionService: EventActionService,
) : EventAction {
    override val actionType = "lost_unique_item"

    override fun execute(context: EventActionContext): EventActionResult {
        val lostProb = (context.params["lostProb"] as? Number)?.toDouble() ?: 0.1
        eventActionService.lostUniqueItem(context.world, lostProb)
        return EventActionResult.Success
    }
}
