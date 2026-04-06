package com.openlogh.service

import com.openlogh.entity.StarRoute
import com.openlogh.entity.StarSystem
import com.openlogh.model.FortressType
import com.openlogh.repository.StarRouteRepository
import com.openlogh.repository.StarSystemRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StarSystemService(
    private val starSystemRepository: StarSystemRepository,
    private val starRouteRepository: StarRouteRepository,
    private val mapService: MapService,
    private val planetService: PlanetService,
) {

    /**
     * Initialize all star systems and routes for a game session from map data.
     *
     * @param sessionId The game session ID
     * @param mapCode The map identifier (default "logh")
     * @param factionMap Region label to faction ID mapping, e.g. {"은하제국" -> 1, "자유행성동맹" -> 2, "페잔 자치령" -> 3}
     * @return Map of mapStarId to saved StarSystem entity
     */
    @Transactional
    fun initializeStarSystems(
        sessionId: Long,
        mapCode: String = "logh",
        factionMap: Map<String, Long>,
    ): Map<Int, StarSystem> {
        val cities = mapService.getCities(mapCode)
        val extras = mapService.getStarSystemExtras(mapCode)
        val extrasById = extras.associateBy { it.id }
        val regions = mapService.getRegions(mapCode)

        // Create StarSystem entities
        val starSystems = cities.map { city ->
            val extra = extrasById[city.id]
            val regionName = regions[city.region] ?: ""
            val factionId = factionMap[regionName] ?: 0L
            val fortressType = extra?.fortressType?.let { typeName ->
                FortressType.entries.find { it.name == typeName }
            } ?: FortressType.NONE

            StarSystem(
                sessionId = sessionId,
                mapStarId = city.id,
                nameKo = city.name,
                nameEn = extra?.nameEn ?: city.name,
                factionId = factionId,
                x = city.x,
                y = city.y,
                spectralType = extra?.spectralType ?: "A",
                starRgb = extra?.starRgb?.toMutableList() ?: mutableListOf(255, 255, 255),
                level = city.level.toShort(),
                region = city.region.toShort(),
                fortressType = fortressType.name,
                fortressGunPower = fortressType.gunPower,
                fortressGunRange = fortressType.gunRange,
                fortressGunCooldown = fortressType.gunCooldownTicks,
                garrisonCapacity = fortressType.garrisonCapacity,
            )
        }

        val savedSystems = starSystemRepository.saveAll(starSystems)
        val systemByMapId = savedSystems.associateBy { it.mapStarId }

        // Create bidirectional StarRoute entries from city connections
        val routeSet = mutableSetOf<Pair<Int, Int>>()
        val routes = mutableListOf<StarRoute>()

        for (city in cities) {
            for (connId in city.connections) {
                val pair = if (city.id < connId) city.id to connId else connId to city.id
                if (routeSet.add(pair)) {
                    // Create both directions
                    routes.add(StarRoute(sessionId = sessionId, fromStarId = city.id, toStarId = connId, distance = 1))
                    routes.add(StarRoute(sessionId = sessionId, fromStarId = connId, toStarId = city.id, distance = 1))
                }
            }
        }

        starRouteRepository.saveAll(routes)

        return systemByMapId
    }

    fun getStarSystemsBySession(sessionId: Long): List<StarSystem> {
        return starSystemRepository.findBySessionId(sessionId)
    }

    fun getStarSystem(sessionId: Long, mapStarId: Int): StarSystem? {
        return starSystemRepository.findBySessionIdAndMapStarId(sessionId, mapStarId)
    }

    fun getRoutes(sessionId: Long): List<StarRoute> {
        return starRouteRepository.findBySessionId(sessionId)
    }

    fun getRoutesFrom(sessionId: Long, fromStarId: Int): List<StarRoute> {
        return starRouteRepository.findBySessionIdAndFromStarId(sessionId, fromStarId)
    }

    fun getFortressSystems(sessionId: Long): List<StarSystem> {
        return starSystemRepository.findBySessionId(sessionId)
            .filter { it.fortressType != "NONE" }
    }

    fun getSystemsByFaction(sessionId: Long, factionId: Long): List<StarSystem> {
        return starSystemRepository.findBySessionIdAndFactionId(sessionId, factionId)
    }

    @Transactional
    fun transferOwnership(starSystem: StarSystem, newFactionId: Long) {
        starSystem.factionId = newFactionId
        starSystemRepository.save(starSystem)

        // Also update all planets in this system
        val planets = planetService.listByWorld(starSystem.sessionId)
            .filter { it.starSystemId == starSystem.id }
        for (planet in planets) {
            planet.factionId = newFactionId
            planetService.save(planet)
        }
    }
}
