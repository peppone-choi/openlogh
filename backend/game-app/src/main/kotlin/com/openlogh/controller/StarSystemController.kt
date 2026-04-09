package com.openlogh.controller

import com.openlogh.dto.GalaxyMapDto
import com.openlogh.dto.StarRouteDto
import com.openlogh.dto.StarSystemDto
import com.openlogh.entity.StarSystem
import com.openlogh.service.PlanetService
import com.openlogh.service.StarSystemService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/world/{sessionId}/galaxy")
class StarSystemController(
    private val starSystemService: StarSystemService,
    private val planetService: PlanetService,
) {

    /**
     * GET /api/world/{sessionId}/galaxy
     * Returns full galaxy map with all systems, routes, and faction territory groupings.
     */
    @GetMapping
    fun getGalaxyMap(@PathVariable sessionId: Long): ResponseEntity<GalaxyMapDto> {
        val systems = starSystemService.getStarSystemsBySession(sessionId)
        if (systems.isEmpty()) {
            return ResponseEntity.notFound().build()
        }

        val routes = starSystemService.getRoutes(sessionId)
        val planets = planetService.listByWorld(sessionId)

        // Build connections map from routes (from -> list of to)
        val connectionsMap = routes.groupBy { it.fromStarId }
            .mapValues { (_, routeList) -> routeList.map { it.toStarId } }

        // Count planets per star system
        val planetCountBySystem = planets
            .filter { it.starSystemId != null }
            .groupBy { it.starSystemId!! }
            .mapValues { it.value.size }

        val systemDtos = systems.map { sys ->
            toDto(sys, connectionsMap[sys.mapStarId] ?: emptyList(), planetCountBySystem[sys.id] ?: 0)
        }

        val routeDtos = routes
            .filter { it.fromStarId < it.toStarId } // deduplicate bidirectional routes
            .map { StarRouteDto(fromStarId = it.fromStarId, toStarId = it.toStarId, distance = it.distance) }

        val factionTerritories = systems
            .filter { it.factionId > 0 }
            .groupBy { it.factionId }
            .mapValues { (_, sysList) -> sysList.map { it.mapStarId } }

        return ResponseEntity.ok(GalaxyMapDto(
            systems = systemDtos,
            routes = routeDtos,
            factionTerritories = factionTerritories,
        ))
    }

    /**
     * GET /api/world/{sessionId}/galaxy/system/{mapStarId}
     * Returns detailed info for a single star system.
     */
    @GetMapping("/system/{mapStarId}")
    fun getStarSystem(
        @PathVariable sessionId: Long,
        @PathVariable mapStarId: Int,
    ): ResponseEntity<StarSystemDto> {
        val system = starSystemService.getStarSystem(sessionId, mapStarId)
            ?: return ResponseEntity.notFound().build()

        val routes = starSystemService.getRoutesFrom(sessionId, mapStarId)
        val connections = routes.map { it.toStarId }

        val planets = planetService.listByWorld(sessionId)
        val planetCount = planets.count { it.starSystemId == system.id }

        return ResponseEntity.ok(toDto(system, connections, planetCount))
    }

    /**
     * GET /api/world/{sessionId}/galaxy/fortresses
     * Returns all fortress star systems.
     */
    @GetMapping("/fortresses")
    fun getFortressSystems(@PathVariable sessionId: Long): ResponseEntity<List<StarSystemDto>> {
        val fortresses = starSystemService.getFortressSystems(sessionId)
        val routes = starSystemService.getRoutes(sessionId)
        val connectionsMap = routes.groupBy { it.fromStarId }
            .mapValues { (_, routeList) -> routeList.map { it.toStarId } }

        val planets = planetService.listByWorld(sessionId)
        val planetCountBySystem = planets
            .filter { it.starSystemId != null }
            .groupBy { it.starSystemId!! }
            .mapValues { it.value.size }

        val dtos = fortresses.map { sys ->
            toDto(sys, connectionsMap[sys.mapStarId] ?: emptyList(), planetCountBySystem[sys.id] ?: 0)
        }

        return ResponseEntity.ok(dtos)
    }

    /**
     * GET /api/world/{sessionId}/galaxy/faction/{factionId}
     * Returns systems owned by a specific faction.
     */
    @GetMapping("/faction/{factionId}")
    fun getSystemsByFaction(
        @PathVariable sessionId: Long,
        @PathVariable factionId: Long,
    ): ResponseEntity<List<StarSystemDto>> {
        val systems = starSystemService.getSystemsByFaction(sessionId, factionId)
        val routes = starSystemService.getRoutes(sessionId)
        val connectionsMap = routes.groupBy { it.fromStarId }
            .mapValues { (_, routeList) -> routeList.map { it.toStarId } }

        val planets = planetService.listByWorld(sessionId)
        val planetCountBySystem = planets
            .filter { it.starSystemId != null }
            .groupBy { it.starSystemId!! }
            .mapValues { it.value.size }

        val dtos = systems.map { sys ->
            toDto(sys, connectionsMap[sys.mapStarId] ?: emptyList(), planetCountBySystem[sys.id] ?: 0)
        }

        return ResponseEntity.ok(dtos)
    }

    private fun toDto(system: StarSystem, connections: List<Int>, planetCount: Int): StarSystemDto {
        return StarSystemDto(
            id = system.id,
            mapStarId = system.mapStarId,
            nameKo = system.nameKo,
            nameEn = system.nameEn,
            factionId = system.factionId,
            x = system.x,
            y = system.y,
            spectralType = system.spectralType,
            starRgb = system.starRgb,
            tier = system.tier,
            region = system.region.toInt(),
            fortressType = system.fortressType,
            fortressGunPower = system.fortressGunPower,
            fortressGunRange = system.fortressGunRange,
            garrisonCapacity = system.garrisonCapacity,
            connections = connections,
            planetCount = planetCount,
        )
    }
}
