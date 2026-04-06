package com.openlogh.service

import com.openlogh.model.UnitType
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.PlanetRepository
import org.springframework.stereotype.Service

/**
 * Population-based formation cap calculation.
 *
 * gin7 core constraint: 1 billion population enables 1 fleet or 6 patrols.
 * Planet.population is stored in units of 10,000 people, so 100,000 = 1 billion.
 */
@Service
class FormationCapService(
    private val planetRepository: PlanetRepository,
    private val fleetRepository: FleetRepository,
) {
    companion object {
        /**
         * Converts UnitType.populationPerUnit (in actual people) to population-field units
         * (stored as x10,000). E.g. 1 billion = 100,000 pop units.
         */
        const val POP_UNIT_SCALE: Long = 10_000L
    }

    /**
     * Calculate max formable units per type for a faction based on total population.
     */
    fun getFormationCaps(sessionId: Long, factionId: Long): Map<UnitType, FormationCap> {
        val totalPop = getFactionPopulation(sessionId, factionId)
        val existingUnits = fleetRepository.findBySessionIdAndFactionId(sessionId, factionId)
        val countByType = existingUnits.groupBy { it.unitType }.mapValues { it.value.size }

        return UnitType.entries.associateWith { type ->
            val max = calculateMax(totalPop, type)
            val current = countByType[type.name] ?: 0
            FormationCap(current = current, max = max)
        }
    }

    /**
     * Check if a faction can form one more unit of this type.
     */
    fun canFormUnit(sessionId: Long, factionId: Long, unitType: UnitType): Boolean {
        if (!unitType.isPopulationLimited) return true

        val totalPop = getFactionPopulation(sessionId, factionId)
        val max = calculateMax(totalPop, unitType)
        val existingUnits = fleetRepository.findBySessionIdAndFactionId(sessionId, factionId)
        val current = existingUnits.count { it.unitType == unitType.name }

        return current < max
    }

    /**
     * Get total population across all faction planets in a session.
     */
    fun getFactionPopulation(sessionId: Long, factionId: Long): Long {
        val planets = planetRepository.findBySessionIdAndFactionId(sessionId, factionId)
        return planets.sumOf { it.population.toLong() }
    }

    private fun calculateMax(totalPop: Long, unitType: UnitType): Int {
        if (!unitType.isPopulationLimited) return Int.MAX_VALUE

        val popPerUnit = unitType.populationPerUnit
        if (popPerUnit <= 0L) return Int.MAX_VALUE

        // Convert totalPop (in x10,000 units) to actual people for comparison
        val totalActual = totalPop * POP_UNIT_SCALE
        return (totalActual / popPerUnit).toInt()
    }
}

/**
 * Represents a formation cap for a specific unit type.
 */
data class FormationCap(val current: Int, val max: Int) {
    val available: Int get() = (max - current).coerceAtLeast(0)
}
