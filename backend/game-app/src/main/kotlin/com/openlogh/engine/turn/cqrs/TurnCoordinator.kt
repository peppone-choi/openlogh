package com.openlogh.engine.turn.cqrs

import com.openlogh.engine.turn.cqrs.memory.DirtyTracker
import com.openlogh.engine.turn.cqrs.memory.InMemoryTurnProcessor
import com.openlogh.engine.turn.cqrs.memory.InMemoryWorldPorts
import com.openlogh.engine.turn.cqrs.memory.WorldStateLoader
import com.openlogh.engine.turn.cqrs.persist.WorldStatePersister
import com.openlogh.entity.SessionState
import com.openlogh.repository.SessionStateRepository
import com.openlogh.service.GameEventService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class TurnCoordinator(
    private val worldStateLoader: WorldStateLoader,
    private val inMemoryTurnProcessor: InMemoryTurnProcessor,
    private val worldStatePersister: WorldStatePersister,
    private val sessionStateRepository: SessionStateRepository,
    private val turnStatusService: TurnStatusService,
    private val gameEventService: GameEventService,
) {
    private val logger = LoggerFactory.getLogger(TurnCoordinator::class.java)

    fun processSession(session: SessionState) {
        val sessionId = session.id.toLong()
        try {
            transition(sessionId, TurnLifecycleState.LOADING)
            val state = worldStateLoader.loadWorldState(sessionId)
            val dirtyTracker = DirtyTracker()
            val ports = InMemoryWorldPorts(state, dirtyTracker)

            transition(sessionId, TurnLifecycleState.PROCESSING)
            val result = inMemoryTurnProcessor.process(session, state, ports)

            transition(sessionId, TurnLifecycleState.PERSISTING)
            worldStatePersister.persist(state, dirtyTracker, state.sessionId)
            sessionStateRepository.save(session)

            transition(sessionId, TurnLifecycleState.PUBLISHING)
            publish(sessionId, result)
        } catch (e: Exception) {
            transition(sessionId, TurnLifecycleState.FAILED)
            logger.error("Turn processing failed for session {}: {}", sessionId, e.message, e)
        } finally {
            transition(sessionId, TurnLifecycleState.IDLE)
        }
    }

    private fun publish(sessionId: Long, result: TurnResult) {
        if (result.events.isEmpty()) return

        result.events.forEach { event ->
            if (event.type == com.openlogh.engine.turn.cqrs.memory.InMemoryTurnProcessor.EVENT_TURN_ADVANCED) {
                val year = (event.payload["year"] as? Int) ?: return@forEach
                val month = (event.payload["month"] as? Int) ?: return@forEach
                gameEventService.broadcastTurnAdvance(sessionId, year, month)
            }
        }
    }

    private fun transition(sessionId: Long, state: TurnLifecycleState) {
        turnStatusService.updateStatus(sessionId, state)
    }
}
