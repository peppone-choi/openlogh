package com.opensam.service

import com.opensam.dto.PublicCachedMapCityResponse
import com.opensam.dto.PublicCachedMapHistoryResponse
import com.opensam.dto.PublicCachedMapResponse
import com.opensam.dto.PublicWorldSummary
import com.opensam.entity.WorldState
import com.opensam.repository.CityRepository
import com.opensam.repository.MessageRepository
import com.opensam.repository.NationRepository
import com.opensam.repository.WorldStateRepository
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Service
class PublicCachedMapService(
    private val worldStateRepository: WorldStateRepository,
    private val cityRepository: CityRepository,
    private val nationRepository: NationRepository,
    private val messageRepository: MessageRepository,
    private val mapService: MapService,
) {
    private data class CacheEntry(
        val expiresAt: Instant,
        val payload: PublicCachedMapResponse,
    )

    private val cache = ConcurrentHashMap<Short, CacheEntry>()

    fun getCachedMap(worldId: Short? = null): PublicCachedMapResponse {
        val now = Instant.now()
        val allWorlds = worldStateRepository.findAll().toList()
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

    private fun buildPayload(world: WorldState, worldSummaries: List<PublicWorldSummary>): PublicCachedMapResponse {
        val worldId = world.id.toLong()
        val mapCode = (world.config["mapCode"] as? String) ?: "che"
        val mapCityByName = mapService.getCities(mapCode).associateBy { it.name }

        val nations = nationRepository.findByWorldId(worldId)
        val nationById = nations.associateBy { it.id }
        val capitalCityIds = nations.mapNotNull { it.capitalCityId }.toSet()
        val cities = cityRepository.findByWorldId(worldId).mapNotNull { city ->
            val mapCity = mapCityByName[city.name] ?: return@mapNotNull null
            val nation = nationById[city.nationId]
            PublicCachedMapCityResponse(
                id = city.id,
                name = city.name,
                x = mapCity.x,
                y = mapCity.y,
                level = mapCity.level,
                nationName = nation?.name ?: "중립",
                nationColor = nation?.color ?: "#4b5563",
                isCapital = city.id in capitalCityIds,
                supplyState = city.supplyState.toInt(),
                state = city.state.toInt(),
            )
        }

        val history = messageRepository.findByWorldIdAndMailboxCodeOrderBySentAtDesc(worldId, "world_history")
            .take(10)
            .map { message ->
                PublicCachedMapHistoryResponse(
                    id = message.id,
                    sentAt = message.sentAt,
                    text = message.payload["message"]?.toString() ?: "",
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
