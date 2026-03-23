package com.opensam.engine.event.actions.game

import com.opensam.engine.EventActionService
import com.opensam.engine.event.EventAction
import com.opensam.engine.event.EventActionContext
import com.opensam.engine.event.EventActionResult
import org.springframework.stereotype.Component

@Component
class MergeInheritPointRankAction(
    private val eventActionService: EventActionService,
) : EventAction {
    override val actionType = "merge_inherit_point_rank"

    override fun execute(context: EventActionContext): EventActionResult {
        eventActionService.mergeInheritPointRank(context.world)
        return EventActionResult.Success
    }
}
