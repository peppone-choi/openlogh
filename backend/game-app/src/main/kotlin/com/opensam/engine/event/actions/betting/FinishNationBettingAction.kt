package com.opensam.engine.event.actions.betting

import com.opensam.engine.EventActionService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class FinishNationBettingAction(
    private val eventActionService: EventActionService,
) : EventAction {
    override val actionType = "finish_nation_betting"

    override fun execute(context: EventActionContext): EventActionResult {
        val bettingId = (context.params["bettingId"] as? Number)?.toLong()
            ?: return EventActionResult.Error("bettingId required")
        eventActionService.finishNationBetting(context.world, bettingId)
        return EventActionResult.Success
    }
}
