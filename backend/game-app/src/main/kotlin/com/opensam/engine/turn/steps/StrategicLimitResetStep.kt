package com.opensam.engine.turn.steps

import com.opensam.engine.turn.TurnContext
import com.opensam.engine.turn.TurnStep
import org.springframework.stereotype.Component

/**
 * Step 1400: Reset strategic command limits and other per-turn decays.
 *
 * Legacy: resetStrategicCommandLimits — strategicCmdLimit, makeLimit,
 * surrenderLimit decay; nation gennum update; spy decay; city state transitions.
 *
 * Note: This step is a marker — the actual logic remains in TurnService
 * because it accesses private helper methods (decaySpyDurations,
 * transitionCityStates, updateDevelCost, decayRefreshScoreTotals).
 * The step exists for pipeline ordering documentation and parity verification.
 */
@Component
class StrategicLimitResetStep : TurnStep {
    override val name = "StrategicLimitReset"
    override val order = 1400

    override fun execute(context: TurnContext) {
        // Delegated back to TurnService.resetStrategicCommandLimits(world)
        // See TurnService.processWorld() for delegation.
    }
}
