package com.openlogh.service

import com.openlogh.dto.GalaxyMapDto
import com.openlogh.dto.StarRouteDto
import com.openlogh.dto.StarSystemDto
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.SessionStateRepository
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Public, unauthenticated, cached view of a session's galaxy map.
 *
 * Mirrors [PublicCachedMapService]: picks the most recently updated world when
 * no worldId is provided, caches the payload for a short time, and returns an
 * empty map when no data is available.
 *
 * Consumed by the lobby/login screens via the /api/public/cached-galaxy proxy.
 */
@Service
class PublicCachedGalaxyService(
    private val sessionStateRepository: SessionStateRepository,
    private val starSystemService: StarSystemService,
    private val planetRepository: PlanetRepository,
) {
    private data class CacheEntry(
        val expiresAt: Instant,
        val payload: GalaxyMapDto,
    )

    private val cache = ConcurrentHashMap<Short, CacheEntry>()

    fun evictCache(worldId: Short) {
        cache.remove(worldId)
    }

    fun getCachedGalaxy(worldId: Short? = null): GalaxyMapDto {
        val now = Instant.now()
        val allWorlds = sessionStateRepository.findAll().toList()

        val targetWorld = if (worldId != null) {
            allWorlds.find { it.id == worldId }
        } else {
            allWorlds.maxByOrNull { it.updatedAt }
        } ?: return emptyGalaxy()

        val cached = cache[targetWorld.id]
        if (cached != null && now.isBefore(cached.expiresAt)) {
            return cached.payload
        }

        val payload = buildPayload(targetWorld.id.toLong())
        cache[targetWorld.id] = CacheEntry(
            expiresAt = now.plus(Duration.ofMinutes(10)),
            payload = payload,
        )
        return payload
    }

    private fun buildPayload(sessionId: Long): GalaxyMapDto {
        val systems = starSystemService.getStarSystemsBySession(sessionId)
        if (systems.isEmpty()) return emptyGalaxy()

        val routes = starSystemService.getRoutes(sessionId)
        val planets = planetRepository.findBySessionId(sessionId)

        val connectionsMap = routes.groupBy { it.fromStarId }
            .mapValues { (_, routeList) -> routeList.map { it.toStarId } }

        val planetCountBySystem = planets
            .filter { it.starSystemId != null }
            .groupBy { it.starSystemId!! }
            .mapValues { it.value.size }

        val systemDtos = systems.map { sys ->
            StarSystemDto(
                id = sys.id,
                mapStarId = sys.mapStarId,
                nameKo = sys.nameKo,
                nameEn = sys.nameEn,
                factionId = sys.factionId,
                x = sys.x,
                y = sys.y,
                spectralType = sys.spectralType,
                starRgb = sys.starRgb,
                level = sys.level.toInt(),
                region = sys.region.toInt(),
                fortressType = sys.fortressType,
                fortressGunPower = sys.fortressGunPower,
                fortressGunRange = sys.fortressGunRange,
                garrisonCapacity = sys.garrisonCapacity,
                connections = connectionsMap[sys.mapStarId] ?: emptyList(),
                planetCount = planetCountBySystem[sys.id] ?: 0,
            )
        }

        val routeDtos = routes
            .filter { it.fromStarId < it.toStarId }
            .map { StarRouteDto(fromStarId = it.fromStarId, toStarId = it.toStarId, distance = it.distance) }

        val factionTerritories = systems
            .filter { it.factionId > 0 }
            .groupBy { it.factionId }
            .mapValues { (_, sysList) -> sysList.map { it.mapStarId } }

        return GalaxyMapDto(
            systems = systemDtos,
            routes = routeDtos,
            factionTerritories = factionTerritories,
        )
    }

    private fun emptyGalaxy(): GalaxyMapDto =
        GalaxyMapDto(
            systems = emptyList(),
            routes = emptyList(),
            factionTerritories = emptyMap(),
        )
}
