package com.openlogh.service

import com.openlogh.entity.SessionState
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.SessionStateRepository
import com.openlogh.repository.WorldHistoryRepository
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class WorldService(
    private val sessionStateRepository: SessionStateRepository,
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val officerRepository: OfficerRepository,
    private val worldHistoryRepository: WorldHistoryRepository,
) {
    companion object {
        const val PHASE_CLOSED = "closed"
        const val PHASE_PRE_OPEN = "pre_open"
        const val PHASE_OPENING = "opening"
        const val PHASE_RUNNING = "running"
    }

    fun getWorld(id: Short): SessionState? = sessionStateRepository.findById(id).orElse(null)

    fun deleteWorld(sessionId: Short) {
        sessionStateRepository.deleteById(sessionId)
    }

    fun getGamePhase(world: SessionState): String {
        val now = OffsetDateTime.now()

        val startTimeStr = world.config["startTime"] as? String
        val openTimeStr = world.config["opentime"] as? String

        if (startTimeStr != null) {
            val startTime = OffsetDateTime.parse(startTimeStr)
            if (now.isBefore(startTime)) return PHASE_CLOSED
        }

        if (openTimeStr != null) {
            val openTime = OffsetDateTime.parse(openTimeStr)
            if (now.isBefore(openTime)) return PHASE_PRE_OPEN
        }

        val startYear = (world.config["startYear"] as? Number)?.toInt() ?: world.currentYear.toInt()
        val yearDiff = world.currentYear - startYear
        val factions = factionRepository.findBySessionId(world.id.toLong())

        if (yearDiff <= 3 || factions.isEmpty()) {
            return PHASE_OPENING
        }

        return PHASE_RUNNING
    }
}
