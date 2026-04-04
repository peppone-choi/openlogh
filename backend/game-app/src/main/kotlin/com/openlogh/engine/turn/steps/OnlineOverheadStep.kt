package com.openlogh.engine.turn.steps

import com.openlogh.engine.turn.TurnContext
import com.openlogh.engine.turn.TurnStep
import org.springframework.stereotype.Component

/**
 * Step 1700: Per-tick online/overhead update.
 *
 * Legacy: per-tick overhead — placeholder for online status bookkeeping.
 * Currently a no-op; actual overhead tracking is handled by the access log
 * infrastructure and traffic snapshots (step 700).
 */
@Component
class OnlineOverheadStep : TurnStep {
    override val name = "OnlineOverhead"
    override val order = 1700

    override fun execute(context: TurnContext) {
        // Placeholder: online/overhead tracking is handled by
        // GeneralAccessLog infrastructure and TrafficSnapshotStep (700).
    }
}
