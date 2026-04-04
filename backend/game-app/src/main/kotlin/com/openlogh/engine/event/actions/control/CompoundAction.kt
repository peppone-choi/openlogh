package com.openlogh.engine.event.actions.control

import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionRegistry
import com.openlogh.engine.event.EventActionResult
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class CompoundAction(
    @Lazy private val registry: EventActionRegistry,
) : EventAction {
    private val log = LoggerFactory.getLogger(javaClass)
    override val actionType = "compound"

    override fun execute(context: EventActionContext): EventActionResult {
        val actions = readStringAnyMapList(context.params["actions"])
            ?: return EventActionResult.Error("actions list required")

        for (subAction in actions) {
            val type = subAction["type"] as? String ?: continue
            val action = try {
                registry.resolve(type)
            } catch (e: Exception) {
                log.warn("[World {}] Compound: unknown sub-action '{}'", context.world.id, type)
                continue
            }
            val subContext = EventActionContext(
                world = context.world,
                params = subAction,
                currentEventId = context.currentEventId,
            )
            val result = action.execute(subContext)
            if (result is EventActionResult.Error) {
                log.warn("[World {}] Compound sub-action '{}' failed: {}", context.world.id, type, result.message)
            }
        }
        return EventActionResult.Success
    }

    private fun readStringAnyMapList(raw: Any?): List<Map<String, Any>>? {
        if (raw !is Iterable<*>) return null
        val result = mutableListOf<Map<String, Any>>()
        raw.forEach { entry ->
            val parsed = readStringAnyMap(entry) ?: return@forEach
            result.add(parsed)
        }
        return result
    }

    private fun readStringAnyMap(raw: Any?): Map<String, Any>? {
        if (raw !is Map<*, *>) return null
        val result = mutableMapOf<String, Any>()
        raw.forEach { (key, value) ->
            if (key is String && value != null) {
                result[key] = value
            }
        }
        return result
    }
}
