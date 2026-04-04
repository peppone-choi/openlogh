package com.openlogh.engine.turn.steps

import com.openlogh.engine.DiplomacyService
import com.openlogh.engine.turn.TurnContext
import com.openlogh.engine.turn.TurnStep
import org.springframework.stereotype.Component

/**
 * Step 1200: Process diplomacy turn (alliance, war declarations, truces).
 *
 * Legacy: processDiplomacyTurn
 */
@Component
class DiplomacyStep(
    private val diplomacyService: DiplomacyService,
) : TurnStep {
    override val name = "Diplomacy"
    override val order = 1200

    override fun execute(context: TurnContext) {
        diplomacyService.processDiplomacyTurn(context.world)
    }
}
