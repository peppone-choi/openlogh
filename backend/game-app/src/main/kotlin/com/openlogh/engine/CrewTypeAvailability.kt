package com.openlogh.engine

import com.openlogh.entity.City
import com.openlogh.entity.General
import com.openlogh.entity.Faction

data class MapCityDefinition(
    val id: Int,
    val name: String,
    val level: Int,
    val region: Int,
)

data class CrewTypeAvailabilityContext(
    val general: General,
    val nation: Faction,
    val mapCities: List<MapCityDefinition>,
    val ownedCities: List<City>,
    val currentYear: Int,
    val startYear: Int,
)

data class ParsedCrewType(
    val id: Int,
    val attackCoef: Map<String, Double>,
    val defenceCoef: Map<String, Double>,
    val requirements: List<Map<String, Any?>>,
)

data class ParsedUnitSet(
    val defaultCrewTypeId: Int,
    val crewTypes: List<ParsedCrewType>,
)

class CrewTypeAvailability {

    fun parseUnitSetDefinition(raw: Map<String, Any?>): ParsedUnitSet {
        val defaultId = (raw["defaultCrewTypeId"] as? Number)?.toInt() ?: 0
        @Suppress("UNCHECKED_CAST")
        val rawTypes = raw["crewTypes"] as? List<Map<String, Any?>> ?: emptyList()
        val crewTypes = rawTypes.map { ct ->
            val id = (ct["id"] as? Number)?.toInt() ?: 0
            val attackCoef: Map<String, Double> = when (val v = ct["attackCoef"]) {
                is Map<*, *> -> v.entries
                    .filter { it.key != null && it.value is Number }
                    .associate { it.key.toString() to (it.value as Number).toDouble() }
                else -> emptyMap()
            }
            val defenceCoef: Map<String, Double> = when (val v = ct["defenceCoef"]) {
                is Map<*, *> -> v.entries
                    .filter { it.key != null && it.value is Number }
                    .associate { it.key.toString() to (it.value as Number).toDouble() }
                else -> emptyMap()
            }
            @Suppress("UNCHECKED_CAST")
            val reqs = ct["requirements"] as? List<Map<String, Any?>> ?: emptyList()
            ParsedCrewType(id, attackCoef, defenceCoef, reqs)
        }
        return ParsedUnitSet(defaultId, crewTypes)
    }

    fun isCrewTypeAvailable(
        unitSet: ParsedUnitSet,
        crewTypeId: Int,
        context: CrewTypeAvailabilityContext,
    ): Boolean {
        val crewType = unitSet.crewTypes.find { it.id == crewTypeId } ?: return false
        val tech = context.nation.techLevel.toInt()
        val relYear = context.currentYear - context.startYear
        val ownedCityNames = context.ownedCities.map { it.name }.toSet()
        val mapCitiesByName = context.mapCities.associateBy { it.name }

        for (req in crewType.requirements) {
            val type = req["type"] as? String ?: continue
            when (type) {
                "ReqTech" -> {
                    val reqTech = (req["tech"] as? Number)?.toInt() ?: 0
                    if (tech < reqTech) return false
                }
                "ReqRegions" -> {
                    @Suppress("UNCHECKED_CAST")
                    val regions = req["regions"] as? List<String> ?: emptyList()
                    if (regions.none { it in ownedCityNames }) return false
                }
                "ReqCitiesWithCityLevel" -> {
                    val reqLevel = (req["level"] as? Number)?.toInt() ?: 0
                    @Suppress("UNCHECKED_CAST")
                    val cities = req["cities"] as? List<String> ?: emptyList()
                    val hasQualifying = cities.any { cityName ->
                        cityName in ownedCityNames &&
                            (mapCitiesByName[cityName]?.level ?: 0) >= reqLevel
                    }
                    if (!hasQualifying) return false
                }
            }
        }
        return true
    }
}
