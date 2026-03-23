package com.openlogh.engine.turn.cqrs.persist

import com.openlogh.engine.turn.cqrs.memory.DirtyTracker
import com.openlogh.engine.turn.cqrs.memory.InMemoryWorldState
import org.springframework.stereotype.Service

@Service
class WorldStatePersister {
    fun persist(state: InMemoryWorldState, tracker: DirtyTracker) {
        // Flush dirty entities to DB
    }
}
