package com.openlogh.engine.event.actions.economy

import com.openlogh.engine.EconomyService
import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class UpdateCitySupplyAction(
    private val economyService: EconomyService,
) : EventAction {
    override val actionType = "update_city_supply"

    override fun execute(context: EventActionContext): EventActionResult {
        economyService.updateCitySupplyState(context.world)
        return EventActionResult.Success
    }
}
