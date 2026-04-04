package com.openlogh.engine.turn.steps

import com.openlogh.engine.EventService
import com.openlogh.engine.turn.TurnContext
import com.openlogh.engine.turn.TurnStep
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
