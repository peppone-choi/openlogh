package com.openlogh.engine.event.actions.economy

import com.openlogh.engine.EconomyService
import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class RandomizeCityTradeRateAction(
    private val economyService: EconomyService,
) : EventAction {
    override val actionType = "randomize_trade_rate"

    override fun execute(context: EventActionContext): EventActionResult {
        economyService.randomizeCityTradeRate(context.world)
        return EventActionResult.Success
    }
}
