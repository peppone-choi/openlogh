package com.opensam.engine.turn.steps

import com.opensam.engine.EventService
import com.opensam.engine.turn.TurnContext
import com.opensam.engine.turn.TurnStep
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
