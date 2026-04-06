package com.openlogh.service

import com.openlogh.engine.ai.OfficerAI
import com.openlogh.engine.ai.PersonalityTrait
import com.openlogh.engine.ai.PersonalityWeights
import com.openlogh.entity.Officer
import com.openlogh.entity.SessionState
import com.openlogh.repository.OfficerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.Duration

/**
 * Handles AI takeover for offline players.
 * When a player has been inactive for longer than the configured threshold,
 * their character continues acting via NPC AI with behavior weighted by
 * the player's stat distribution.
 */
@Service
class OfflinePlayerAIService(
    private val officerRepository: OfficerRepository,
    private val officerAI: OfficerAI,
) {
    private val logger = LoggerFactory.getLogger(OfflinePlayerAIService::class.java)

    companion object {
        /** Default offline threshold: 30 real-time minutes */
        const val DEFAULT_OFFLINE_THRESHOLD_MINUTES = 30L
    }

    /**
     * Find all offline player officers in a session and execute AI decisions for them.
     * Called from the tick engine periodically.
     */
    fun processOfflinePlayers(world: SessionState) {
        val thresholdMinutes = (world.config["offlineThresholdMinutes"] as? Number)?.toLong()
            ?: DEFAULT_OFFLINE_THRESHOLD_MINUTES
        val cutoff = OffsetDateTime.now().minus(Duration.ofMinutes(thresholdMinutes))

        val offlinePlayers = officerRepository.findBySessionId(world.id.toLong())
            .filter { officer ->
                // Player characters (npcState=0) with a userId who are offline
                officer.npcState.toInt() == 0 &&
                    officer.userId != null &&
                    officer.factionId != 0L &&
                    isOffline(officer, cutoff)
            }

        if (offlinePlayers.isEmpty()) return

        logger.debug("Processing {} offline players in session {}", offlinePlayers.size, world.id)

        for (officer in offlinePlayers) {
            try {
                // Infer personality from player's stats if not already set
                ensurePersonality(officer)

                // Execute AI decision using OfficerAI with the officer's personality
                val action = officerAI.decideAndExecute(officer, world)
                logger.info(
                    "Offline player {} ({}) AI decided: {} [personality={}]",
                    officer.id, officer.name, action,
                    PersonalityTrait.fromString(officer.personality),
                )
            } catch (e: Exception) {
                logger.warn("AI decision failed for offline player {}: {}", officer.id, e.message)
            }
        }
    }

    /**
     * Check if an officer is offline based on lastAccessAt or turnTime.
     */
    private fun isOffline(officer: Officer, cutoff: OffsetDateTime): Boolean {
        val lastAccess = officer.lastAccessAt ?: officer.turnTime
        return lastAccess.isBefore(cutoff)
    }

    /**
     * Ensure the officer has a personality set.
     * For player characters, infer from their stat distribution.
     */
    private fun ensurePersonality(officer: Officer) {
        if (officer.personality != "BALANCED" && officer.personality.isNotBlank()) return

        // Only auto-infer for player characters going offline
        if (officer.npcState.toInt() == 0) {
            val inferred = PersonalityWeights.inferFromStats(
                leadership = officer.leadership.toInt(),
                command = officer.command.toInt(),
                intelligence = officer.intelligence.toInt(),
                politics = officer.politics.toInt(),
                administration = officer.administration.toInt(),
                mobility = officer.mobility.toInt(),
                attack = officer.attack.toInt(),
                defense = officer.defense.toInt(),
            )
            officer.personality = inferred.name
            officerRepository.save(officer)
            logger.debug("Inferred personality {} for offline player {} ({})",
                inferred, officer.id, officer.name)
        }
    }

    /**
     * Update last access time for an officer (called when player performs an action).
     */
    fun recordAccess(officerId: Long) {
        val officer = officerRepository.findById(officerId).orElse(null) ?: return
        officer.lastAccessAt = OffsetDateTime.now()
        officerRepository.save(officer)
    }
}
