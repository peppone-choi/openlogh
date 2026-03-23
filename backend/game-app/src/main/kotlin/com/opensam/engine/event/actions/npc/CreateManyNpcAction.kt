package com.opensam.engine.event.actions.npc

import com.opensam.engine.EventActionService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class CreateManyNpcAction(
    private val eventActionService: EventActionService,
) : EventAction {
    override val actionType = "create_many_npc"

    override fun execute(context: EventActionContext): EventActionResult {
        val npcCount = (context.params["npcCount"] as? Number)?.toInt() ?: 10
        val fillCnt = (context.params["fillCnt"] as? Number)?.toInt() ?: 0
        eventActionService.createManyNPC(context.world, npcCount, fillCnt)
        return EventActionResult.Success
    }
}
