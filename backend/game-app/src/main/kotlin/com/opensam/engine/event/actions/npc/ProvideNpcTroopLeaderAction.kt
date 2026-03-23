package com.opensam.engine.event.actions.npc

import com.opensam.engine.NpcSpawnService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
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
