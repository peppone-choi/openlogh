package com.openlogh.engine.event.actions.control

import com.openlogh.engine.EventActionService
import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class ChangeCityAction(
    private val eventActionService: EventActionService,
) : EventAction {
    override val actionType = "change_city"

    override fun execute(context: EventActionContext): EventActionResult {
        val target = context.params["target"]
        val changes = readStringAnyMap(context.params["changes"])
            ?: return EventActionResult.Error("changes required")
        eventActionService.changeCity(context.world, target, changes)
        return EventActionResult.Success
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
