package com.opensam.engine.event.actions.game

import com.opensam.engine.EventActionService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
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
