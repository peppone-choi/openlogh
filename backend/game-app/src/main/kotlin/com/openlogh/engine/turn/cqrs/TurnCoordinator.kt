package com.openlogh.engine.turn.cqrs

import com.openlogh.engine.turn.cqrs.memory.DirtyTracker
import com.openlogh.engine.turn.cqrs.memory.InMemoryTurnProcessor
import com.openlogh.engine.turn.cqrs.memory.InMemoryWorldPorts
import com.openlogh.engine.turn.cqrs.memory.WorldStateLoader
import com.openlogh.engine.turn.cqrs.persist.WorldStatePersister
import com.openlogh.entity.WorldState
import com.openlogh.repository.WorldStateRepository
import com.openlogh.service.GameEventService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class TurnCoordinator(
    private val worldStateLoader: WorldStateLoader,
    private val inMemoryTurnProcessor: InMemoryTurnProcessor,
    private val worldStatePersister: WorldStatePersister,
    private val worldStateRepository: WorldStateRepository,
    private val turnStatusService: TurnStatusService,
    private val gameEventService: GameEventService,
) {
    private val logger = LoggerFactory.getLogger(TurnCoordinator::class.java)

    fun processWorld(world: WorldState) {
        val worldId = world.id.toLong()
        try {
            transition(worldId, TurnLifecycleState.LOADING)
            val state = worldStateLoader.loadWorldState(worldId)
            val dirtyTracker = DirtyTracker()
            val ports = InMemoryWorldPorts(state, dirtyTracker)

            transition(worldId, TurnLifecycleState.PROCESSING)
            val result = inMemoryTurnProcessor.process(world, state, ports)

            transition(worldId, TurnLifecycleState.PERSISTING)
            worldStatePersister.persist(state, dirtyTracker, state.worldId)
            worldStateRepository.save(world)

            transition(worldId, TurnLifecycleState.PUBLISHING)
            publish(worldId, result)
        } catch (e: Exception) {
            transition(worldId, TurnLifecycleState.FAILED)
            logger.error("Turn processing failed for world {}: {}", worldId, e.message, e)
        } finally {
            transition(worldId, TurnLifecycleState.IDLE)
        }
    }

    private fun publish(worldId: Long, result: TurnResult) {
        if (result.events.isEmpty()) return

        result.events.forEach { event ->
            if (event.type == com.openlogh.engine.turn.cqrs.memory.InMemoryTurnProcessor.EVENT_TURN_ADVANCED) {
                val year = (event.payload["year"] as? Int) ?: return@forEach
                val month = (event.payload["month"] as? Int) ?: return@forEach
                gameEventService.broadcastTurnAdvance(worldId, year, month)
            }
        }
    }

    private fun transition(worldId: Long, state: TurnLifecycleState) {
        turnStatusService.updateStatus(worldId, state)
    }
}
