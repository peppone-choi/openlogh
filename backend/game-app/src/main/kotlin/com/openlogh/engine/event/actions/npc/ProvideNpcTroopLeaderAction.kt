package com.openlogh.engine.event.actions.npc

import com.openlogh.engine.NpcSpawnService
import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class ProvideNpcTroopLeaderAction(
    private val npcSpawnService: NpcSpawnService,
) : EventAction {
    override val actionType = "provide_npc_troop_leader"

    override fun execute(context: EventActionContext): EventActionResult {
        npcSpawnService.provideNpcTroopLeaders(context.world)
        return EventActionResult.Success
    }
}
