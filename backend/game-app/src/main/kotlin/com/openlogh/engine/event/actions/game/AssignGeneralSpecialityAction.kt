package com.openlogh.engine.event.actions.game

import com.openlogh.engine.EventActionService
import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class AssignGeneralSpecialityAction(
    private val eventActionService: EventActionService,
) : EventAction {
    override val actionType = "assign_general_speciality"

    override fun execute(context: EventActionContext): EventActionResult {
        eventActionService.assignGeneralSpeciality(context.world)
        return EventActionResult.Success
    }
}
