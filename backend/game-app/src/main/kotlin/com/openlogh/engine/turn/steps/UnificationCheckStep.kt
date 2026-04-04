package com.openlogh.engine.turn.steps

import com.openlogh.engine.UnificationService
import com.openlogh.engine.turn.TurnContext
import com.openlogh.engine.turn.TurnStep
import org.springframework.stereotype.Component

/**
 * Step 1600: Check and settle unification (unified state freeze).
 *
 * Legacy: checkAndSettleUnification — determines if a single nation
 * controls all cities and triggers the game-ending unification flow.
 */
@Component
class UnificationCheckStep(
    private val unificationService: UnificationService,
) : TurnStep {
    override val name = "UnificationCheck"
    override val order = 1600

    override fun execute(context: TurnContext) {
        unificationService.checkAndSettleUnification(context.world)
    }
}
