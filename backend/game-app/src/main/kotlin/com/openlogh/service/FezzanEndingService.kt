package com.openlogh.service

import com.openlogh.repository.FactionRepository
import com.openlogh.repository.EventRepository
import com.openlogh.entity.Event
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Handles the Fezzan domination ending.
 * When a faction defaults on 3+ Fezzan loans, Fezzan economically dominates that faction.
 */
@Service
class FezzanEndingService(
    private val factionRepository: FactionRepository,
    private val eventRepository: EventRepository,
    private val fezzanService: FezzanService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Trigger the Fezzan ending for a dominated faction.
     * This is a game-ending condition.
     */
    @Transactional
    fun triggerFezzanEnding(sessionId: Long, dominatedFactionId: Long) {
        val faction = factionRepository.findById(dominatedFactionId).orElseThrow {
            IllegalArgumentException("Faction not found: $dominatedFactionId")
        }

        // Create scenario event for the ending
        val event = Event(
            sessionId = sessionId,
            targetCode = "immediate",
            priority = 999,
            condition = mutableMapOf("type" to "Always" as Any),
            action = mutableMapOf(
                "type" to "compound" as Any,
                "actions" to listOf(
                    mapOf(
                        "type" to "ScenarioMessage",
                        "message" to "페잔 자치령이 ${faction.name}을(를) 경제적으로 지배하게 되었다! 막대한 부채로 인해 ${faction.name}은(는) 사실상 페잔의 속국이 되었다.",
                    ),
                    mapOf("type" to "GameEnd", "result" to "fezzan_domination"),
                ) as Any,
            ),
        )
        eventRepository.save(event)

        log.info("[Session {}] FEZZAN ENDING triggered! Faction {} dominated by Fezzan",
            sessionId, dominatedFactionId)
    }

    /**
     * Check and trigger Fezzan ending if conditions are met.
     * Called periodically from the tick engine.
     */
    @Transactional
    fun checkAndTrigger(sessionId: Long) {
        val result = fezzanService.checkFezzanEnding(sessionId)
        if (result.triggered && result.dominatedFactionId != null) {
            triggerFezzanEnding(sessionId, result.dominatedFactionId)
        }
    }
}
