package com.opensam.engine.event.actions.npc

import com.opensam.engine.EventActionService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
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
