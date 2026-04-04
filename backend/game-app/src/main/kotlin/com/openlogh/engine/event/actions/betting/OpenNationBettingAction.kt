package com.openlogh.engine.event.actions.betting

import com.openlogh.engine.EventActionService
import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class OpenNationBettingAction(
    private val eventActionService: EventActionService,
) : EventAction {
    override val actionType = "open_nation_betting"

    override fun execute(context: EventActionContext): EventActionResult {
        val nationCnt = (context.params["nationCnt"] as? Number)?.toInt() ?: 1
        val bonusPoint = (context.params["bonusPoint"] as? Number)?.toInt() ?: 0
        eventActionService.openNationBetting(context.world, nationCnt, bonusPoint)
        return EventActionResult.Success
    }
}
