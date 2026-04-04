package com.openlogh.engine.turn.steps

import com.openlogh.engine.EconomyService
import com.openlogh.engine.turn.TurnContext
import com.openlogh.engine.turn.TurnStep
import org.springframework.stereotype.Component

/**
 * Step 300: Pre-month economy update (income processing, war income).
 *
 * Legacy: preUpdateMonthly() — processIncome + processWarIncome
 *
 * NOTE: Execution is handled directly in TurnService.processWorld() before advanceMonth()
 * to match legacy ordering (preUpdateMonthly runs before turnDate advances).
 * This pipeline step is skipped to avoid double-execution.
 */
@Component
class EconomyPreUpdateStep(
    private val economyService: EconomyService,
) : TurnStep {
    override val name = "EconomyPreUpdate"
    override val order = 300

    override fun shouldSkip(context: TurnContext): Boolean = true

    override fun execute(context: TurnContext) {
        economyService.preUpdateMonthly(context.world)
    }
}
