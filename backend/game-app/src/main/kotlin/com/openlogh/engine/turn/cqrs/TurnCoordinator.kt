package com.openlogh.engine.turn.cqrs

import com.openlogh.engine.turn.cqrs.memory.DirtyTracker
import com.openlogh.engine.turn.cqrs.memory.InMemoryTurnProcessor
import com.openlogh.engine.turn.cqrs.memory.WorldStateLoader
import com.openlogh.engine.turn.cqrs.persist.WorldStatePersister
import com.openlogh.entity.SessionState
import com.openlogh.repository.SessionStateRepository
import com.openlogh.service.GameEventService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TurnCoordinator(
    private val worldStateLoader: WorldStateLoader,
    private val inMemoryTurnProcessor: InMemoryTurnProcessor,
    private val worldStatePersister: WorldStatePersister,
    private val sessionStateRepository: SessionStateRepository,
    private val turnStatusService: TurnStatusService,
    private val gameEventService: GameEventService,
) {
    companion object {
        private val log = LoggerFactory.getLogger(TurnCoordinator::class.java)
    }

    fun processWorld(world: SessionState) {
        val worldId = world.id.toLong()
        try {
            // LOADING
            turnStatusService.updateStatus(worldId, TurnLifecycleState.LOADING)
            val state = worldStateLoader.loadWorldState(worldId)

            // PROCESSING
            turnStatusService.updateStatus(worldId, TurnLifecycleState.PROCESSING)
            val tracker = DirtyTracker()
            val result = inMemoryTurnProcessor.process(world, state, tracker)

            // PERSISTING
            turnStatusService.updateStatus(worldId, TurnLifecycleState.PERSISTING)
            worldStatePersister.persist(state, tracker)
            sessionStateRepository.save(world)

            // PUBLISHING
            turnStatusService.updateStatus(worldId, TurnLifecycleState.PUBLISHING)
            if (result.advancedTurns > 0) {
                gameEventService.broadcastTurnAdvance(
                    worldId,
                    world.currentYear.toInt(),
                    world.currentMonth.toInt(),
                )
            }

            turnStatusService.updateStatus(worldId, TurnLifecycleState.IDLE)
        } catch (e: Exception) {
            log.error("CQRS turn processing failed for world {}", worldId, e)
            turnStatusService.updateStatus(worldId, TurnLifecycleState.FAILED)
            turnStatusService.updateStatus(worldId, TurnLifecycleState.IDLE)
        }
    }
}
