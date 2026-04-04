package com.openlogh.engine.turn.steps

import com.openlogh.engine.EconomyService
import com.openlogh.engine.turn.TurnContext
import com.openlogh.engine.turn.TurnStep
import org.springframework.stereotype.Component

/**
 * Step 1100: Process disaster/boom and randomize city trade rates.
 *
 * Legacy: processDisasterOrBoom + randomizeCityTradeRate
 */
@Component
class DisasterAndTradeStep(
    private val economyService: EconomyService,
) : TurnStep {
    override val name = "DisasterAndTrade"
    override val order = 1100

    override fun execute(context: TurnContext) {
        economyService.processDisasterOrBoom(context.world)
        economyService.randomizeCityTradeRate(context.world)
    }
}
