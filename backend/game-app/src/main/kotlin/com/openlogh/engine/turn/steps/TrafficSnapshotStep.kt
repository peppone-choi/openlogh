package com.openlogh.engine.turn.steps

import com.openlogh.engine.turn.TurnContext
import com.openlogh.engine.turn.TurnStep
import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.entity.TrafficSnapshot
import com.openlogh.repository.TrafficSnapshotRepository
import org.springframework.stereotype.Component

/**
 * Step 700: Record traffic snapshot (online count, refresh counter).
 *
 * Legacy: recentTraffic parity.
 */
@Component
class TrafficSnapshotStep(
    private val trafficSnapshotRepository: TrafficSnapshotRepository,
    private val worldPortFactory: JpaWorldPortFactory,
) : TurnStep {
    override val name = "TrafficSnapshot"
    override val order = 700

    override fun execute(context: TurnContext) {
        val onlineCount = worldPortFactory.create(context.worldId).allGenerals().count { it.userId != null }
        val snapshot = TrafficSnapshot(
            worldId = context.worldId,
            year = context.world.currentYear,
            month = context.world.currentMonth,
            refresh = (context.world.meta["refresh"] as? Number)?.toInt() ?: 0,
            online = onlineCount,
        )
        trafficSnapshotRepository.save(snapshot)
        // Reset per-turn refresh counter
        context.world.meta["refresh"] = 0
    }
}
