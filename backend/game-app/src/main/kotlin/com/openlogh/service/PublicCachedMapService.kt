package com.openlogh.service

import com.openlogh.dto.PublicCachedMapCityResponse
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
    private val sessionStateRepository: SessionStateRepository,
    private val factionRepository: FactionRepository,
    private val planetRepository: PlanetRepository,
    private val recordRepository: RecordRepository,
    private val mapService: MapService,
    private val planetService: PlanetService,
) {
    private data class CacheEntry(
        val expiresAt: Instant,
        val payload: PublicCachedMapResponse,
    )

    private val cache = ConcurrentHashMap<Short, CacheEntry>()

    fun evictCache(worldId: Short) {
        cache.remove(worldId)
    }

    fun getCachedMap(worldId: Short? = null): PublicCachedMapResponse {
        val now = Instant.now()
        val allWorlds = sessionStateRepository.findAll().toList()
        val worldSummaries = allWorlds.map { PublicWorldSummary(it.id.toLong(), it.name) }

        val targetWorld = if (worldId != null) {
            allWorlds.find { it.id == worldId }
        } else {
            allWorlds.maxByOrNull { it.updatedAt }
        } ?: return PublicCachedMapResponse(
            available = false,
            worldId = null,
            worldName = null,
            mapCode = null,
            cities = emptyList(),
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
        val worldId = world.id.toLong()
        val mapCode = (world.config["mapCode"] as? String) ?: "che"
        val mapCityByName = mapService.getCities(mapCode).associateBy { it.name }

        val nations = factionRepository.findBySessionId(worldId)
        val nationById = nations.associateBy { it.id }
        val cities = planetRepository.findBySessionId(worldId).mapNotNull { city ->
            val mapCity = mapCityByName[city.name] ?: return@mapNotNull null
            val nation = nationById[city.nationId]
            val isCapital = nation != null && nation.capitalCityId == city.id
            PublicCachedMapCityResponse(
                id = city.id,
                name = city.name,
                x = mapCity.x,
                y = mapCity.y,
                level = mapCity.level,
                region = mapCity.region,
                nationName = nation?.name ?: "",
                nationColor = nation?.color ?: "#4b5563",
                nationAbbr = nation?.abbreviation?.ifBlank { null },
                isCapital = isCapital,
                supplyState = city.supplyState.toInt(),
                state = city.state.toInt(),
            )
        }

        val history = recordRepository.findBySessionIdAndRecordTypeOrderByCreatedAtDesc(worldId, "world_history")
            .take(10)
            .map { record ->
                val isScenarioInit = record.payload["scenarioInit"] == true
                PublicCachedMapHistoryResponse(
                    id = record.id,
                    sentAt = record.createdAt,
                    text = record.payload["message"]?.toString() ?: "",
                    year = if (isScenarioInit) null else record.year.toInt(),
                    month = if (isScenarioInit) null else record.month.toInt(),
                )
            }

        return PublicCachedMapResponse(
            available = true,
            worldId = worldId,
            worldName = world.name,
            mapCode = mapCode,
            currentYear = world.currentYear.toInt(),
            currentMonth = world.currentMonth.toInt(),
            cities = cities,
            history = history,
            worlds = worldSummaries,
        )
    }
}
