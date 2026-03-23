package com.opensam.engine.event.actions.misc

import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
import com.opensam.entity.Message
import com.opensam.repository.MessageRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class NoticeAction(
    private val messageRepository: MessageRepository,
) : EventAction {
    private val log = LoggerFactory.getLogger(javaClass)
    override val actionType = "notice"

    override fun execute(context: EventActionContext): EventActionResult {
        val message = context.params["message"] as? String ?: ""
        messageRepository.save(
            Message(
                worldId = context.world.id.toLong(),
                mailboxCode = "notice",
                messageType = "notice",
                payload = mutableMapOf(
                    "message" to message,
                    "year" to context.world.currentYear.toInt(),
                    "month" to context.world.currentMonth.toInt(),
                ),
            )
        )
        log.info("[World {}] Notice: {}", context.world.id, message)
        return EventActionResult.Success
    }
}
