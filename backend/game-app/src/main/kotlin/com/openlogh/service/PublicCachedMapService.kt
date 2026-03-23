package com.openlogh.service

import com.openlogh.dto.PublicCachedMapPlanetResponse
import com.openlogh.dto.PublicCachedMapHistoryResponse
import com.openlogh.dto.PublicCachedMapResponse
import com.openlogh.dto.PublicWorldSummary
import com.openlogh.entity.SessionState
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.RecordRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.SessionStateRepository
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class PublicCachedMapService(
    private val worldStateRepository: SessionStateRepository,
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val recordRepository: RecordRepository,
    private val mapService: MapService,
) {
    private data class CacheEntry(
        val expiresAt: Instant,
        val payload: PublicCachedMapResponse,
    )

    private val cache = ConcurrentHashMap<Short, CacheEntry>()

    fun evictCache(sessionId: Short) {
        cache.remove(sessionId)
    }

    fun getCachedMap(sessionId: Short? = null): PublicCachedMapResponse {
        val now = Instant.now()
        val allWorlds = worldStateRepository.findAll().toList()
        val worldSummaries = allWorlds.map { PublicWorldSummary(it.id.toLong(), it.name) }

        val targetWorld = if (sessionId != null) {
            allWorlds.find { it.id == sessionId }
        } else {
            allWorlds.maxByOrNull { it.updatedAt }
        } ?: return PublicCachedMapResponse(
            available = false,
            sessionId = null,
            worldName = null,
            mapCode = null,
            planets = emptyList(),
            history = emptyList(),
            worlds = worldSummaries,
        )

        val cached = cache[targetWorld.id]
        if (cached != null && now.isBefore(cached.expiresAt)) {
            return cached.payload.copy(worlds = worldSummaries)
        }

        val payload = buildPayload(targetWorld, worldSummaries)
        cache[targetWorld.id] = CacheEntry(
            expiresAt = now.plus(Duration.ofMinutes(10)),
            payload = payload,
        )
        return payload
    }

    private fun buildPayload(world: SessionState, worldSummaries: List<PublicWorldSummary>): PublicCachedMapResponse {
        val sessionId = world.id.toLong()
        val mapCode = (world.config["mapCode"] as? String) ?: "che"
        val mapCityByName = mapService.getCities(mapCode).associateBy { it.name }

        val nations = factionRepository.findBySessionId(sessionId)
        val nationById = nations.associateBy { it.id }
        val planets = planetRepository.findBySessionId(sessionId).mapNotNull { city ->
            val mapCity = mapCityByName[city.name] ?: return@mapNotNull null
            val faction = nationById[city.factionId]
            val isCapital = faction != null && faction.capitalPlanetId == city.id
            PublicCachedMapPlanetResponse(
                id = city.id,
                name = city.name,
                x = mapCity.x,
                y = mapCity.y,
                level = mapCity.level,
                region = mapCity.region,
                factionName = faction?.name ?: "",
                factionColor = faction?.color ?: "#4b5563",
                isCapital = isCapital,
                supplyState = city.supplyState.toInt(),
                state = city.state.toInt(),
            )
        }

        val history = recordRepository.findBySessionIdAndRecordTypeOrderByCreatedAtDesc(sessionId, "world_history")
            .take(10)
            .map { record ->
                PublicCachedMapHistoryResponse(
                    id = record.id,
                    sentAt = record.createdAt,
                    text = record.payload["message"]?.toString() ?: "",
                    year = record.year.toInt(),
                    month = record.month.toInt(),
                )
            }

        return PublicCachedMapResponse(
            available = true,
            sessionId = sessionId,
            worldName = world.name,
            mapCode = mapCode,
            currentYear = world.currentYear.toInt(),
            currentMonth = world.currentMonth.toInt(),
            planets = planets,
            history = history,
            worlds = worldSummaries,
        )
    }
}
