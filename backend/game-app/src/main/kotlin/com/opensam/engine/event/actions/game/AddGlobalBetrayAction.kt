package com.opensam.engine.event.actions.game

import com.opensam.engine.EventActionService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class AddGlobalBetrayAction(
    private val eventActionService: EventActionService,
) : EventAction {
    override val actionType = "add_global_betray"

    override fun execute(context: EventActionContext): EventActionResult {
        val cnt = (context.params["cnt"] as? Number)?.toInt() ?: 1
        val ifMax = (context.params["ifMax"] as? Number)?.toInt() ?: 0
        eventActionService.addGlobalBetray(context.world, cnt, ifMax)
        return EventActionResult.Success
    }
}
