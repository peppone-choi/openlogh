package com.opensam.engine.event

import com.opensam.engine.RandUtil
import com.opensam.entity.WorldState

/**
 * Interface for all event actions.
 * Each implementation handles one action type string from the event JSON.
 */
interface EventAction {
    val actionType: String
    fun execute(context: EventActionContext): EventActionResult
}

data class EventActionContext(
    val world: WorldState,
    val params: Map<String, Any>,
    val currentEventId: Long = 0,
)

sealed class EventActionResult {
    object Success : EventActionResult()
    data class Error(val message: String) : EventActionResult()
}

class UnknownEventActionException(actionType: String) :
    IllegalArgumentException("Unknown event action type: $actionType")
