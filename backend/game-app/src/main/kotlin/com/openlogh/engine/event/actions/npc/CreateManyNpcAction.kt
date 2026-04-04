package com.openlogh.engine.event.actions.npc

import com.openlogh.engine.EventActionService
import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
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
