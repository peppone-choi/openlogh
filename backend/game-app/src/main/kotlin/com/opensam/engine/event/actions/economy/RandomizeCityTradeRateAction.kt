package com.opensam.engine.event.actions.economy

import com.opensam.engine.EconomyService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
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
