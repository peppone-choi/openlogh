package com.openlogh.engine.event.actions.misc

import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
import com.openlogh.service.HistoryService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class LogAction(
    private val historyService: HistoryService,
) : EventAction {
    private val log = LoggerFactory.getLogger(javaClass)
    override val actionType = "log"

    override fun execute(context: EventActionContext): EventActionResult {
        val message = context.params["message"] as? String ?: ""
        log.info("[World {}] History: {}", context.world.id, message)
        historyService.logWorldHistory(
            worldId = context.world.id.toLong(),
            message = message,
            year = context.world.currentYear.toInt(),
            month = context.world.currentMonth.toInt(),
        )
        return EventActionResult.Success
    }
}
