package com.openlogh.engine.turn.steps

import com.openlogh.engine.EventService
import com.openlogh.engine.turn.TurnContext
import com.openlogh.engine.turn.TurnStep
import org.springframework.stereotype.Component

/**
 * Step 900: Dispatch MONTH events.
 *
 * Legacy: runEventHandler(Month) / eventService.dispatchEvents(world, "MONTH")
 */
@Component
class MonthEventStep(
    private val eventService: EventService,
) : TurnStep {
    override val name = "MonthEvent"
    override val order = 900

    override fun execute(context: TurnContext) {
        eventService.dispatchEvents(context.world, "MONTH")
    }
}
