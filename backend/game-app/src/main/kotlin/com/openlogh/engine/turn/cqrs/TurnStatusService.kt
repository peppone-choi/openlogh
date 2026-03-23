package com.openlogh.engine.turn.cqrs

import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class TurnStatusService {

    private val statusMap = ConcurrentHashMap<Long, TurnLifecycleState>()

    fun getStatus(worldId: Long): TurnLifecycleState {
        return statusMap.getOrDefault(worldId, TurnLifecycleState.IDLE)
    }

    fun updateStatus(worldId: Long, state: TurnLifecycleState) {
        statusMap[worldId] = state
    }
}
