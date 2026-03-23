package com.opensam.engine.event.actions.betting

import com.opensam.engine.EventActionService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
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
