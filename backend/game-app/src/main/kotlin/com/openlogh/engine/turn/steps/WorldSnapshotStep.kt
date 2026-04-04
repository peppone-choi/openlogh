package com.openlogh.engine.turn.steps

import com.openlogh.engine.turn.TurnContext
import com.openlogh.engine.turn.TurnStep
import com.openlogh.service.WorldService
import org.springframework.stereotype.Component

/**
 * Step 600: Capture world snapshot for history map replay.
 *
 * Legacy: world_history.event_type=snapshot
 */
@Component
class WorldSnapshotStep(
    private val worldService: WorldService,
) : TurnStep {
    override val name = "WorldSnapshot"
    override val order = 600

    override fun execute(context: TurnContext) {
        worldService.captureSnapshot(context.world)
    }
}
