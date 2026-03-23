package com.opensam.engine.event.actions.control

import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
import com.opensam.repository.EventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DeleteEventAction(
    private val eventRepository: EventRepository,
) : EventAction {
    private val log = LoggerFactory.getLogger(javaClass)
    override val actionType = "delete_event"

    override fun execute(context: EventActionContext): EventActionResult {
        val eventId = (context.params["eventId"] as? Number)?.toLong()
            ?: return EventActionResult.Error("eventId required")
        eventRepository.deleteById(eventId)
        log.info("[World {}] Deleted event #{}", context.world.id, eventId)
        return EventActionResult.Success
    }
}
