package com.openlogh.engine.event.actions.npc

import com.openlogh.engine.NpcSpawnService
import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class RaiseNpcNationAction(
    private val npcSpawnService: NpcSpawnService,
) : EventAction {
    override val actionType = "raise_npc_nation"

    override fun execute(context: EventActionContext): EventActionResult {
        npcSpawnService.checkNpcSpawn(context.world)
        return EventActionResult.Success
    }
}
