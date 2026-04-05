package com.openlogh.engine.turn.steps

import com.openlogh.engine.turn.TurnContext
import com.openlogh.engine.turn.TurnStep
import com.openlogh.service.FactionService
import org.springframework.stereotype.Component

/**
 * Step 1300: Recalculate war front status for all nations.
 *
 * Legacy: SetNationFront parity.
 */
@Component
class WarFrontRecalcStep(
    private val factionService: FactionService,
) : TurnStep {
    override val name = "WarFrontRecalc"
    override val order = 1300

    override fun execute(context: TurnContext) {
        factionService.recalcAllFronts(context.sessionId)
    }
}
