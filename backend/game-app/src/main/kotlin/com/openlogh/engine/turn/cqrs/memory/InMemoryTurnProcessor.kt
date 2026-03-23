package com.openlogh.engine.turn.cqrs.memory

import com.openlogh.engine.turn.cqrs.TurnResult
import com.openlogh.entity.SessionState
import org.springframework.stereotype.Service

@Service
class InMemoryTurnProcessor {
    fun process(world: SessionState, state: InMemoryWorldState, tracker: DirtyTracker): TurnResult {
        return TurnResult(advancedTurns = 0, events = emptyList())
    }
}
