package com.opensam.engine.event.actions.npc

import com.opensam.engine.NpcSpawnService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
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
