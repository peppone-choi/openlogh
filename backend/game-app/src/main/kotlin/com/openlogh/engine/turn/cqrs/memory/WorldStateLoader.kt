package com.openlogh.engine.turn.cqrs.memory

import org.springframework.stereotype.Service

@Service
class WorldStateLoader {
    fun loadWorldState(worldId: Long): InMemoryWorldState {
        return InMemoryWorldState(worldId = worldId)
    }
}
