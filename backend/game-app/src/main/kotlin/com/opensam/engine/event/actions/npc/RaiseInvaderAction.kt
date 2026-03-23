package com.opensam.engine.event.actions.npc

import com.opensam.engine.NpcSpawnService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class RaiseInvaderAction(
    private val npcSpawnService: NpcSpawnService,
) : EventAction {
    override val actionType = "raise_invader"

    override fun execute(context: EventActionContext): EventActionResult {
        npcSpawnService.raiseInvader(context.world)
        return EventActionResult.Success
    }
}
