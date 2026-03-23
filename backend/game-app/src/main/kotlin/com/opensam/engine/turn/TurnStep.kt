package com.opensam.engine.turn

import com.opensam.entity.WorldState
import java.time.OffsetDateTime

/**
 * Single step in the monthly turn pipeline.
 * Each step is a Spring @Component and auto-registers in TurnPipeline.
 */
interface TurnStep {
    val name: String
    val order: Int
    fun execute(context: TurnContext)
    fun shouldSkip(context: TurnContext): Boolean = false
}

/**
 * Context passed through the monthly turn pipeline.
 *
 * @param world the mutable world state (steps may mutate it)
 * @param worldId convenience Long copy of world.id
 * @param year current year at the start of this turn iteration (before advanceMonth)
 * @param month current month at the start of this turn iteration (before advanceMonth)
 * @param previousYear year recorded before advanceMonth (yearbook snapshot target)
 * @param previousMonth month recorded before advanceMonth (yearbook snapshot target)
 * @param nextTurnAt the OffsetDateTime boundary this iteration processes up to
 * @param advanceMonthFn callback to TurnService.advanceMonth (uses private helpers)
 * @param resetStrategicLimitsFn callback to TurnService.resetStrategicCommandLimits (uses private helpers)
 */
class TurnContext(
    val world: WorldState,
    val worldId: Long,
    val year: Int,
    val month: Int,
    val previousYear: Int,
    val previousMonth: Int,
    val nextTurnAt: OffsetDateTime,
    val advanceMonthFn: () -> Unit = {},
    val resetStrategicLimitsFn: () -> Unit = {},
)
