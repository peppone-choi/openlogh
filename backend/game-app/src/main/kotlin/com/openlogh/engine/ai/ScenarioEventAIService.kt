package com.openlogh.engine.ai

import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.engine.turn.cqrs.persist.toEntity
import com.openlogh.engine.turn.cqrs.persist.toSnapshot
import com.openlogh.entity.Faction
import com.openlogh.entity.SessionState
import com.openlogh.model.CoupPhase
import com.openlogh.service.GameEventService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Scenario event AI: detects special game conditions and triggers narrative events.
 *
 * Currently handles:
 *   - Empire civil war trigger: when a coup reaches ACTIVE phase and military support
 *     from coup supporters reaches or exceeds the threshold ratio of total faction ships,
 *     the internal civil war begins.
 *
 * Called every 100 ticks from TickEngine.processPolitics().
 */
@Service
class ScenarioEventAIService(
    private val worldPortFactory: JpaWorldPortFactory,
    private val gameEventService: GameEventService,
) {
    private val logger = LoggerFactory.getLogger(ScenarioEventAIService::class.java)

    companion object {
        /**
         * Military power ratio threshold: if coup supporters hold >= 40% of total empire
         * military ships, the coup triggers a full civil war split.
         */
        const val CIVIL_WAR_MILITARY_THRESHOLD = 0.4
    }

    fun processTick(world: SessionState) {
        val sessionId = world.id.toLong()
        val ports = worldPortFactory.create(sessionId)

        val empireFactions = ports.allFactions()
            .map { it.toEntity() }
            .filter { it.factionType == "empire" }

        for (faction in empireFactions) {
            checkEmpireCoupCondition(faction, world, sessionId, ports)
        }
    }

    private fun checkEmpireCoupCondition(
        faction: Faction,
        world: SessionState,
        sessionId: Long,
        ports: com.openlogh.engine.turn.cqrs.persist.WorldPorts,
    ) {
        // Skip if civil war already triggered (idempotent)
        if (faction.meta["civilWarTriggered"] == true) return

        // Only act when coup is ACTIVE
        val coupPhaseCode = faction.meta["coupPhase"] as? String ?: return
        val coupPhase = CoupPhase.fromCode(coupPhaseCode)
        if (coupPhase != CoupPhase.ACTIVE) return

        // coupLeaderId must be set
        @Suppress("UNUSED_VARIABLE")
        val coupLeaderId = (faction.meta["coupLeaderId"] as? Number)?.toLong() ?: return

        // coupActiveSinceTick must be set
        val activeSinceTick = (faction.meta["coupActiveSinceTick"] as? Number)?.toLong() ?: return
        val ticksActive = world.tickCount - activeSinceTick

        // Gather officers and tally military support
        val allOfficers = ports.officersByFaction(faction.id).map { it.toEntity() }
        val coupSupporters = allOfficers.filter { it.meta["coupSupport"] == true }

        val totalMilitary = allOfficers.sumOf { it.ships }
        val supportMilitary = coupSupporters.sumOf { it.ships }

        // Trigger civil war if:
        //   (a) enough military support accumulated (ratio >= threshold), OR
        //   (b) coup has been active for 300+ ticks without resolution
        val supportRatio = if (totalMilitary > 0) supportMilitary.toDouble() / totalMilitary else 0.0
        if (supportRatio >= CIVIL_WAR_MILITARY_THRESHOLD || ticksActive > 300) {
            triggerCivilWar(faction, sessionId, ports)
        }
    }

    private fun triggerCivilWar(
        faction: Faction,
        sessionId: Long,
        ports: com.openlogh.engine.turn.cqrs.persist.WorldPorts,
    ) {
        logger.info("Civil war triggered for empire faction {} in session {}", faction.id, sessionId)
        faction.meta["civilWarTriggered"] = true
        faction.meta["civilWarStartTick"] = System.currentTimeMillis()
        ports.putFaction(faction.toSnapshot())
        gameEventService.broadcastWorldUpdate(sessionId, mapOf("type" to "civilWar", "factionId" to faction.id))
    }
}
