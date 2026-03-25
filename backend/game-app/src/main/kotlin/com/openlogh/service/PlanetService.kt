package com.openlogh.service

import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.PlanetRepository
import org.springframework.stereotype.Service

@Service
class PlanetService(
    private val planetRepository: PlanetRepository,
    private val mapService: MapService,
    private val officerRepository: OfficerRepository,
    private val factionRepository: FactionRepository,
) {
    fun canonicalRegionForDisplay(planet: Planet): Short {
        val cities = mapService.getCities("logh")
        val mapCity = cities.find { it.name == planet.name }
        return mapCity?.region?.toShort() ?: planet.region
    }

    fun listByWorldMaskedForGeneral(sessionId: Long, officer: Officer): List<Planet> {
        val planets = planetRepository.findBySessionId(sessionId)
        return planets.map { planet ->
            if (shouldMask(planet, officer)) {
                maskPlanet(planet)
            } else {
                planet
            }
        }
    }

    private fun shouldMask(planet: Planet, officer: Officer): Boolean {
        if (planet.id == officer.planetId) return false
        if (officer.factionId == 0L) return false
        if (planet.factionId == officer.factionId) return false
        return true
    }

    private fun maskPlanet(planet: Planet): Planet {
        planet.production = 0
        planet.commerce = 0
        planet.security = 0
        planet.population = 0
        planet.orbitalDefense = 0
        planet.fortress = 0
        return planet
    }
}
