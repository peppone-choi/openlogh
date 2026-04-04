package com.openlogh.engine.event.actions.game

import com.openlogh.engine.EventActionService
import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
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
