package com.opensam.engine.turn.steps

import com.opensam.engine.DiplomacyService
import com.opensam.engine.turn.TurnContext
import com.opensam.engine.turn.TurnStep
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
