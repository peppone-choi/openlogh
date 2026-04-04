package com.openlogh.engine.event.actions.npc

import com.openlogh.engine.EventActionService
import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class RegNpcAction(
    private val eventActionService: EventActionService,
) : EventAction {
    override val actionType = "reg_npc"

    override fun execute(context: EventActionContext): EventActionResult {
        val params = context.params.filterKeys { it != "type" }
        eventActionService.regNPC(context.world, params)
        return EventActionResult.Success
    }
}
