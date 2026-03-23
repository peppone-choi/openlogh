package com.opensam.engine.event.actions.game

import com.opensam.engine.EventActionService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class AutoDeleteInvaderAction(
    private val eventActionService: EventActionService,
) : EventAction {
    override val actionType = "auto_delete_invader"

    override fun execute(context: EventActionContext): EventActionResult {
        val nationId = (context.params["nationId"] as? Number)?.toLong()
            ?: return EventActionResult.Error("nationId required")
        eventActionService.autoDeleteInvader(context.world, nationId, context.currentEventId)
        return EventActionResult.Success
    }
}
