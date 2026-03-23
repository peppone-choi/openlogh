package com.opensam.engine.event.actions.npc

import com.opensam.engine.EventActionService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
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
