package com.openlogh.engine.event.actions.betting

import com.openlogh.engine.EventActionService
import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
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
