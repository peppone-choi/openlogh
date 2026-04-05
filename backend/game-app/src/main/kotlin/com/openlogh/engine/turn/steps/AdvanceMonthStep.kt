package com.openlogh.engine.turn.steps

import com.openlogh.engine.turn.TurnContext
import com.openlogh.engine.turn.TurnStep
import org.springframework.stereotype.Component

/**
 * Step 400: Advance the world calendar by one month.
 *
 * Legacy: turnDate() / advanceMonth()
 * This step is a marker — actual month advancement is handled by TurnService
 * because it mutates SessionState.currentYear/currentMonth which affects all
 * subsequent steps' context.
 */
@Component
class AdvanceMonthStep : TurnStep {
    override val name = "AdvanceMonth"
    override val order = 400

    override fun execute(context: TurnContext) {
        // Handled by TurnService.advanceMonth(world) directly.
        // The pipeline context already records previousYear/previousMonth
        // before this step, so downstream steps see the correct values.
    }
}
