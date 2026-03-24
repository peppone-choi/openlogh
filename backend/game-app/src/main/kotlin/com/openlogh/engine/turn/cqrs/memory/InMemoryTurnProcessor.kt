package com.openlogh.engine.turn.cqrs.memory

import com.openlogh.engine.AgeGrowthService
import com.openlogh.engine.CommandPointService
import com.openlogh.engine.EconomyService
import com.openlogh.engine.EventService
import com.openlogh.engine.OfficerMaintenanceService
import com.openlogh.engine.UnificationService
import com.openlogh.engine.modifier.OfficerLevelModifier
import com.openlogh.engine.turn.cqrs.TurnResult
import com.openlogh.entity.SessionState
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

/**
 * CQRS in-memory turn processor.
 *
 * Processes elapsed ticks against an in-memory snapshot of world state,
 * delegating to existing sub-services for each monthly step.
 * DirtyTracker records all mutations for batch persistence.
 */
@Service
class InMemoryTurnProcessor(
    private val economyService: EconomyService,
    private val eventService: EventService,
    private val officerMaintenanceService: OfficerMaintenanceService,
    private val commandPointService: CommandPointService,
    private val ageGrowthService: AgeGrowthService,
    private val unificationService: UnificationService,
    private val officerLevelModifier: OfficerLevelModifier,
) {
    companion object {
        private val log = LoggerFactory.getLogger(InMemoryTurnProcessor::class.java)
    }

    fun process(world: SessionState, state: InMemoryWorldState, tracker: DirtyTracker): TurnResult {
        val now = OffsetDateTime.now()
        var advancedTurns = 0
        val events = mutableListOf<Any>()

        // Process each elapsed tick
        while (world.updatedAt.plusSeconds(world.tickSeconds.toLong()) <= now) {
            // Advance month
            world.currentMonth = (world.currentMonth + 1).toShort()
            if (world.currentMonth > 12) {
                world.currentMonth = 1
                world.currentYear = (world.currentYear + 1).toShort()
            }

            // Advance updatedAt by tick duration
            world.updatedAt = world.updatedAt.plusSeconds(world.tickSeconds.toLong())

            // Monthly sub-services (resilient - continue on failure)
            tryRun("economyService.preUpdateMonthly") { economyService.preUpdateMonthly(world) }
            tryRun("economyService.postUpdateMonthly") { economyService.postUpdateMonthly(world) }
            tryRun("economyService.processDisasterOrBoom") { economyService.processDisasterOrBoom(world) }
            tryRun("economyService.randomizeCityTradeRate") { economyService.randomizeCityTradeRate(world) }
            tryRun("eventService.dispatchEvents") { eventService.dispatchEvents(world, "monthly") }
            tryRun("officerMaintenanceService") {
                officerMaintenanceService.processOfficerMaintenance(world, state.officers)
            }
            tryRun("commandPointService.recoverAllCp") {
                for (officer in state.officers) {
                    commandPointService.recoverCp(officer)
                    tracker.markDirty(officer)
                }
            }
            tryRun("ageGrowthService.processMonthlyGrowth") {
                ageGrowthService.processMonthlyGrowth(world)
            }
            tryRun("officerLevelModifier.applyMonthlyModifiers") {
                officerLevelModifier.applyMonthlyModifiers(state.officers, world)
                state.officers.forEach { tracker.markDirty(it) }
            }

            // Mark all factions dirty (economy changes)
            state.factions.forEach { tracker.markDirty(it) }
            state.planets.forEach { tracker.markDirty(it) }

            // Unification check
            tryRun("unificationService.checkAndSettleUnification") {
                unificationService.checkAndSettleUnification(world)
            }

            advancedTurns++
        }

        tracker.markDirty(world)
        return TurnResult(advancedTurns = advancedTurns, events = events)
    }

    private fun tryRun(label: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            log.warn("CQRS sub-service failed [{}]: {}", label, e.message)
        }
    }
}
