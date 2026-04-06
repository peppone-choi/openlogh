package com.openlogh.engine.ai

import com.openlogh.engine.DeterministicRng
import com.openlogh.engine.turn.cqrs.persist.JpaWorldPortFactory
import com.openlogh.engine.turn.cqrs.persist.toEntity
import com.openlogh.entity.SessionState
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Slot-based round-robin scheduler for faction AI processing.
 * Processes exactly ONE faction per processTick() call to avoid O(n) processing spikes.
 *
 * Active factions (factionRank > 0, not "fezzan") are cycled in stable order.
 * Slot index is stored per-session in a local map (reset if world reloads).
 */
@Service
class FactionAIScheduler(
    private val factionAI: FactionAIPort,
    private val worldPortFactory: JpaWorldPortFactory,
) {
    private val logger = LoggerFactory.getLogger(FactionAIScheduler::class.java)

    // Per-session slot index (session id -> next slot index)
    private val slotIndex = mutableMapOf<Long, Int>()

    fun processTick(world: SessionState) {
        val sessionId = world.id.toLong()
        val ports = worldPortFactory.create(sessionId)

        // Get active AI-controlled factions (exclude fezzan — has dedicated service)
        val activeFactions = ports.allFactions()
            .map { it.toEntity() }
            .filter { it.factionRank > 0 && it.factionType != "fezzan" }
            .sortedBy { it.id }  // stable order

        if (activeFactions.isEmpty()) return

        val idx = slotIndex.getOrDefault(sessionId, 0) % activeFactions.size
        val faction = activeFactions[idx]
        slotIndex[sessionId] = (idx + 1) % activeFactions.size

        val rng = DeterministicRng.create(
            (world.config["hiddenSeed"] as? String) ?: "${world.id}",
            "FactionAI", world.currentYear, world.currentMonth, faction.id
        )

        try {
            val action = factionAI.decideNationAction(faction, world, rng)
            logger.debug("Faction AI [{}] decided: {}", faction.id, action)
        } catch (e: Exception) {
            logger.warn("FactionAI error for faction {} in session {}: {}", faction.id, sessionId, e.message)
        }
    }

    /** Reset slot for a session (called on world reset/end) */
    fun resetSlot(sessionId: Long) {
        slotIndex.remove(sessionId)
    }
}
