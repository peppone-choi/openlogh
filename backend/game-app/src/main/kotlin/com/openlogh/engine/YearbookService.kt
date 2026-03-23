package com.openlogh.engine

import com.fasterxml.jackson.databind.ObjectMapper
import com.openlogh.entity.SessionState
import com.openlogh.entity.YearbookHistory
import com.openlogh.repository.*
import org.springframework.stereotype.Service
import java.security.MessageDigest

@Service
class YearbookService(
    private val sessionStateRepository: SessionStateRepository,
    private val planetRepository: PlanetRepository,
    private val factionRepository: FactionRepository,
    private val officerRepository: OfficerRepository,
    private val messageRepository: MessageRepository,
    private val yearbookHistoryRepository: YearbookHistoryRepository,
    private val objectMapper: ObjectMapper,
) {
    fun saveMonthlySnapshot(sessionId: Long, year: Int, month: Int): YearbookHistory {
        val world = sessionStateRepository.findById(sessionId.toShort()).orElseThrow {
            IllegalArgumentException("World not found: $sessionId")
        }

        val planets = planetRepository.findBySessionId(sessionId)
        val factions = factionRepository.findBySessionId(sessionId)
        val officers = officerRepository.findBySessionId(sessionId)
        val messages = messageRepository.findBySessionIdAndYearAndMonthOrderBySentAtAsc(sessionId, year, month)

        val mapData = planets.map { planet ->
            mapOf(
                "id" to planet.id,
                "name" to planet.name,
                "factionId" to planet.factionId,
                "population" to planet.population,
                "production" to planet.production,
                "commerce" to planet.commerce,
                "security" to planet.security,
            )
        }

        val nationData = factions.map { faction ->
            mutableMapOf<String, Any>(
                "id" to faction.id,
                "name" to faction.name,
                "color" to faction.color,
                "funds" to faction.funds,
                "supplies" to faction.supplies,
                "factionRank" to faction.factionRank,
                "officerCount" to faction.officerCount,
            ).also { m -> faction.capitalPlanetId?.let { m["capitalPlanetId"] = it } }
        }

        val officerData = officers.map { officer ->
            mapOf(
                "id" to officer.id,
                "name" to officer.name,
                "factionId" to officer.factionId,
                "leadership" to officer.leadership,
                "command" to officer.command,
                "intelligence" to officer.intelligence,
                "funds" to officer.funds,
                "supplies" to officer.supplies,
                "experience" to officer.experience,
                "dedication" to officer.dedication,
                "npcState" to officer.npcState,
            )
        }

        val snapshot = mapOf(
            "year" to year,
            "month" to month,
            "map" to mapData,
            "nations" to nationData,
            "officers" to officerData,
            "messages" to messages.map { it.id },
        )

        val json = objectMapper.writeValueAsString(snapshot)
        val hash = sha256(json)

        val existing = yearbookHistoryRepository.findBySessionIdAndYearAndMonth(
            sessionId, year.toShort(), month.toShort(),
        )
        @Suppress("UNCHECKED_CAST")
        val nationsList = nationData.toMutableList() as MutableList<Map<String, Any>>
        if (existing != null) {
            existing.map = snapshot.toMutableMap()
            existing.nations = nationsList
            existing.hash = hash
            return yearbookHistoryRepository.save(existing)
        }

        return yearbookHistoryRepository.save(YearbookHistory(
            sessionId = sessionId,
            year = year.toShort(),
            month = month.toShort(),
            map = snapshot.toMutableMap(),
            nations = nationsList,
            hash = hash,
        ))
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun List<Map<String, Any?>>.toMutableMap(): MutableMap<String, Any> {
        return mutableMapOf("data" to this)
    }

    private fun Map<String, Any?>.toNonNullMutableMap(): MutableMap<String, Any> {
        val result = mutableMapOf<String, Any>()
        for ((k, v) in this) {
            if (v != null) result[k] = v
        }
        return result
    }
}
