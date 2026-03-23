package com.opensam.engine.turn.steps

import com.opensam.engine.YearbookService
import com.opensam.engine.turn.TurnContext
import com.opensam.engine.turn.TurnStep
import org.springframework.stereotype.Component

/**
 * Step 500: Save yearbook (map/nation state) snapshot for the previous month.
 *
 * Legacy: yearbookHandler.onMonthChanged — core2026 parity.
 */
@Component
class YearbookSnapshotStep(
    private val yearbookService: YearbookService,
) : TurnStep {
    override val name = "YearbookSnapshot"
    override val order = 500

    override fun execute(context: TurnContext) {
        yearbookService.saveMonthlySnapshot(context.worldId, context.previousYear, context.previousMonth)
    }
}
