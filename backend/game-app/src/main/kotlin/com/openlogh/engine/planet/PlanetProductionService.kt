package com.openlogh.engine.planet

import com.openlogh.entity.Planet
import com.openlogh.entity.SessionState
import com.openlogh.repository.PlanetRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Feature 21.6 - Per-Planet Production Table
 *
 * Each planet has auto-production items based on its level/type.
 * Production runs monthly during turn processing.
 * Products: ship units, supplies, ground units based on planet production stat.
 * Reads planet.meta["productionTable"] or derives from planet level.
 */
@Service
class PlanetProductionService(
    private val planetRepository: PlanetRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(PlanetProductionService::class.java)

        // Base production per level per month
        private const val SUPPLIES_PER_PRODUCTION_POINT = 2
        private const val SHIPS_PER_PRODUCTION_POINT = 1
        private const val MAX_SUPPLIES_CAP_MULTIPLIER = 5  // max supplies = production * this
    }

    /**
     * Run monthly production for all planets in a session.
     */
    @Transactional
    fun processMonthlyProduction(world: SessionState) {
        val sessionId = world.id.toLong()
        val planets = planetRepository.findBySessionId(sessionId)

        var totalSuppliesProduced = 0
        var totalShipsProduced = 0

        for (planet in planets) {
            try {
                val result = producePlanet(planet)
                totalSuppliesProduced += result.suppliesProduced
                totalShipsProduced += result.shipsProduced
                if (result.suppliesProduced > 0 || result.shipsProduced > 0) {
                    planetRepository.save(planet)
                }
            } catch (e: Exception) {
                log.warn("Error processing production for planet {}: {}", planet.id, e.message)
            }
        }

        log.debug(
            "Session {} monthly production: supplies={} ships={}",
            sessionId, totalSuppliesProduced, totalShipsProduced,
        )
    }

    /**
     * Produce items for a single planet.
     * Uses planet.meta["productionTable"] if present, otherwise derives from planet level.
     */
    fun producePlanet(planet: Planet): ProductionResult {
        val table = getProductionTable(planet)
        var suppliesProduced = 0
        var shipsProduced = 0

        // Supplies production
        val suppliesOutput = (table["suppliesOutput"] as? Number)?.toInt() ?: 0
        if (suppliesOutput > 0) {
            val maxSupplies = planet.production * MAX_SUPPLIES_CAP_MULTIPLIER
            val canProduce = maxOf(0, maxSupplies - planet.supplies)
            val actual = minOf(suppliesOutput, canProduce)
            planet.supplies += actual
            suppliesProduced = actual
        }

        // Ship production (stored as planet garrison resource)
        val shipsOutput = (table["shipsOutput"] as? Number)?.toInt() ?: 0
        if (shipsOutput > 0) {
            val maxShips = planet.garrisonSet + shipsOutput
            planet.garrisonSet = maxShips
            shipsProduced = shipsOutput
        }

        // Orbital defense regeneration
        val defenseRegen = (table["defenseRegen"] as? Number)?.toInt() ?: 0
        if (defenseRegen > 0 && planet.orbitalDefense < planet.orbitalDefenseMax) {
            planet.orbitalDefense = minOf(
                planet.orbitalDefenseMax,
                planet.orbitalDefense + defenseRegen,
            )
        }

        return ProductionResult(suppliesProduced, shipsProduced)
    }

    private fun getProductionTable(planet: Planet): Map<String, Any> {
        // Use explicit table from meta if present
        @Suppress("UNCHECKED_CAST")
        val explicit = planet.meta["productionTable"] as? Map<String, Any>
        if (explicit != null) return explicit

        // Derive from planet level and production stat
        val level = planet.level.toInt()
        val productionStat = planet.production

        val suppliesOutput = productionStat * SUPPLIES_PER_PRODUCTION_POINT
        val shipsOutput = when {
            level >= 5 -> productionStat * SHIPS_PER_PRODUCTION_POINT
            level >= 3 -> productionStat / 2
            else -> 0
        }
        val defenseRegen = when {
            level >= 4 -> 10
            level >= 2 -> 5
            else -> 2
        }

        return mapOf(
            "suppliesOutput" to suppliesOutput,
            "shipsOutput" to shipsOutput,
            "defenseRegen" to defenseRegen,
        )
    }

    data class ProductionResult(
        val suppliesProduced: Int,
        val shipsProduced: Int,
    )
}
