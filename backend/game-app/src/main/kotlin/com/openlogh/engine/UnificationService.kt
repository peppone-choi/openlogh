package com.openlogh.engine

import com.openlogh.entity.SessionState
import com.openlogh.repository.*
import com.openlogh.service.HistoryService
import org.springframework.stereotype.Service

@Service
class UnificationService(
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val officerRepository: OfficerRepository,
    private val appUserRepository: AppUserRepository,
    private val hallOfFameRepository: HallOfFameRepository,
    private val sovereignRepository: SovereignRepository,
    private val oldFactionRepository: OldFactionRepository,
    private val oldOfficerRepository: OldOfficerRepository,
    private val gameHistoryRepository: GameHistoryRepository,
    private val messageRepository: MessageRepository,
    private val historyService: HistoryService,
) {
    fun checkAndSettleUnification(world: SessionState) {
        val isUnited = (world.config["isUnited"] as? Number)?.toInt() ?: 0
        if (isUnited != 0) return

        val sessionId = world.id.toLong()
        val factions = factionRepository.findBySessionId(sessionId)

        // Count active factions (factionRank > 0)
        val activeFactions = factions.filter { it.factionRank > 0 }
        if (activeFactions.size != 1) return

        val winner = activeFactions[0]

        // Check all planets are owned by the winner
        val planets = planetRepository.findBySessionId(sessionId)
        if (planets.isEmpty()) return
        if (planets.any { it.factionId != winner.id && it.factionId != 0L }) return
        // All non-neutral planets must belong to winner
        val unownedNeutral = planets.filter { it.factionId == 0L }
        if (unownedNeutral.isNotEmpty()) return

        // Mark as united
        world.config["isUnited"] = 2
        val currentRefresh = (world.config["refreshLimit"] as? Number)?.toInt() ?: 30000
        world.config["refreshLimit"] = currentRefresh * 100

        // Save game history
        val serverId = world.name ?: "unknown"
        val existingHistory = gameHistoryRepository.findByServerId(serverId)
        if (existingHistory == null) {
            val gh = com.openlogh.entity.GameHistory(serverId = serverId, winnerNation = winner.id)
            gh.meta["winnerFactionName"] = winner.name
            gh.meta["year"] = world.currentYear.toInt()
            gh.meta["month"] = world.currentMonth.toInt()
            gameHistoryRepository.save(gh)
        }

        // Save old faction records
        for (faction in factions) {
            val existing = oldFactionRepository.findByServerIdAndNation(serverId, faction.id)
            if (existing == null) {
                val of = com.openlogh.entity.OldFaction(serverId = serverId, nation = faction.id)
                of.data["name"] = faction.name
                of.data["color"] = faction.color
                oldFactionRepository.save(of)
            }
        }

        // Save sovereign record
        val sov = com.openlogh.entity.Sovereign(sessionId = serverId)
        sov.aux["sessionId"] = sessionId
        sov.aux["factionId"] = winner.id
        sov.aux["officerId"] = winner.supremeCommanderId
        sovereignRepository.save(sov)

        // Notify
        messageRepository.save(com.openlogh.entity.Message(
            sessionId = sessionId,
            mailboxCode = "system",
            messageType = "unification",
            payload = mutableMapOf(
                "factionId" to winner.id,
                "factionName" to winner.name,
            ),
        ))
    }
}
