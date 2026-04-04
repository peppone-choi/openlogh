package com.openlogh.engine.event.actions.control

import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
import com.openlogh.repository.EventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DeleteSelfAction(
    private val eventRepository: EventRepository,
) : EventAction {
    private val log = LoggerFactory.getLogger(javaClass)
    override val actionType = "delete_self"

    override fun execute(context: EventActionContext): EventActionResult {
        if (context.currentEventId > 0) {
            eventRepository.deleteById(context.currentEventId)
            log.info("[World {}] Event #{} deleted itself", context.world.id, context.currentEventId)
        }
        return EventActionResult.Success
    }
}
