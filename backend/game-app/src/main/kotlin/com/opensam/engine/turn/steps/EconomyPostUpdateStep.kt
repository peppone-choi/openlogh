package com.opensam.engine.turn.steps

import com.opensam.engine.EconomyService
import com.opensam.engine.turn.TurnContext
import com.opensam.engine.turn.TurnStep
import org.springframework.stereotype.Component

/**
 * Step 1000: Post-month economy update (semi-annual processing, population/wall growth).
 *
 * Legacy: postUpdateMonthly()
 */
@Component
class EconomyPostUpdateStep(
    private val economyService: EconomyService,
) : TurnStep {
    override val name = "EconomyPostUpdate"
    override val order = 1000

    override fun execute(context: TurnContext) {
        economyService.postUpdateMonthly(context.world)
    }
}
