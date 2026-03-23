package com.opensam.engine.turn.steps

import com.opensam.engine.turn.TurnContext
import com.opensam.engine.turn.TurnStep
import com.opensam.service.NationService
import org.springframework.stereotype.Component

/**
 * Step 1300: Recalculate war front status for all nations.
 *
 * Legacy: SetNationFront parity.
 */
@Component
class WarFrontRecalcStep(
    private val nationService: NationService,
) : TurnStep {
    override val name = "WarFrontRecalc"
    override val order = 1300

    override fun execute(context: TurnContext) {
        nationService.recalcAllFronts(context.worldId)
    }
}
