package com.opensam.engine.event

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Registry that maps action type strings to EventAction implementations.
 * All @Component classes implementing EventAction are auto-collected by Spring.
 */
@Component
class EventActionRegistry(actions: List<EventAction>) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val registry: Map<String, EventAction> = actions.associateBy { it.actionType }

    init {
        log.info("EventActionRegistry loaded {} actions: {}", registry.size, registry.keys.sorted())
    }

    fun resolve(actionType: String): EventAction =
        registry[actionType] ?: throw UnknownEventActionException(actionType)

    fun has(actionType: String): Boolean = actionType in registry

    fun allTypes(): Set<String> = registry.keys
}
