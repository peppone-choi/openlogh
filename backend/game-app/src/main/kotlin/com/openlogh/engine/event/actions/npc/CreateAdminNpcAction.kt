package com.openlogh.engine.event.actions.npc

import com.openlogh.engine.EventActionService
import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class CreateAdminNpcAction(
    private val eventActionService: EventActionService,
) : EventAction {
    override val actionType = "create_admin_npc"

    override fun execute(context: EventActionContext): EventActionResult {
        eventActionService.createAdminNPC(context.world)
        return EventActionResult.Success
    }
}
