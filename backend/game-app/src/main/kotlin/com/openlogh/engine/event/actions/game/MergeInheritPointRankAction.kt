package com.openlogh.engine.event.actions.game

import com.openlogh.engine.EventActionService
import com.openlogh.engine.event.EventAction
import com.openlogh.engine.event.EventActionContext
import com.openlogh.engine.event.EventActionResult
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
