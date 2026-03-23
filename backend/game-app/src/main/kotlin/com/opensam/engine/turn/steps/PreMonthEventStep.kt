package com.opensam.engine.turn.steps

import com.opensam.engine.EventService
import com.opensam.engine.turn.TurnContext
import com.opensam.engine.turn.TurnStep
import org.springframework.stereotype.Component

/**
 * Step 200: Dispatch PRE_MONTH events.
 *
 * Legacy: runEventHandler(PreMonth) / eventService.dispatchEvents(world, "PRE_MONTH")
 *
 * NOTE: Execution is handled directly in TurnService.processWorld() before advanceMonth()
 * to match legacy ordering (PreMonth runs before turnDate advances).
 * This pipeline step is skipped to avoid double-execution.
 */
@Component
class PreMonthEventStep(
    private val eventService: EventService,
) : TurnStep {
    override val name = "PreMonthEvent"
    override val order = 200

    override fun shouldSkip(context: TurnContext): Boolean = true

    override fun execute(context: TurnContext) {
        eventService.dispatchEvents(context.world, "PRE_MONTH")
    }
}
