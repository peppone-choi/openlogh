package com.openlogh.engine.event.actions.misc

import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
import com.openlogh.entity.Message
import com.openlogh.repository.MessageRepository
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
                sessionId = context.world.id.toLong(),
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
