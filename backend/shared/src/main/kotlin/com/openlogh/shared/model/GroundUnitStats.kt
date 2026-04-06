package com.openlogh.shared.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

/**
 * Ground unit (육전병) statistics data model.
 * Loaded from ground_unit_stats.json at runtime.
 */

@JsonIgnoreProperties(ignoreUnknown = true)
data class GroundUnitStatsRoot(
    val factions: Map<String, FactionGroundUnits> = emptyMap(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FactionGroundUnits(
    val factionNameKo: String = "",
    val units: List<GroundUnitDefinition> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GroundUnitDefinition(
    val typeId: String = "",
    val nameKo: String = "",
    val nameEn: String = "",
    val trainingCost: Int = 0,
    val groundAttack: Int = 0,
    val groundDefense: Int = 0,
)

/**
 * Registry for looking up ground unit stats by faction and type.
 *
 * Usage:
 * ```
 * val registry = GroundUnitStatsRegistry.load()
 * val unit = registry.lookup("empire", "imperial_guard")
 * val allEmpire = registry.getUnits("empire")
 * ```
 */
class GroundUnitStatsRegistry private constructor(
    private val root: GroundUnitStatsRoot,
) {
    /** Indexed lookup: faction -> typeId -> GroundUnitDefinition */
    private val index: Map<String, Map<String, GroundUnitDefinition>> =
        root.factions.mapValues { (_, factionUnits) ->
            factionUnits.units.associateBy { it.typeId }
        }

    /**
     * Look up a ground unit by faction and type ID.
     */
    fun lookup(faction: String, typeId: String): GroundUnitDefinition? =
        index[faction]?.get(typeId)

    /**
     * Get all ground units for a faction.
     */
    fun getUnits(faction: String): List<GroundUnitDefinition> =
        root.factions[faction]?.units ?: emptyList()

    /**
     * Get all available faction IDs.
     */
    fun getFactions(): Set<String> = root.factions.keys

    companion object {
        private val mapper = ObjectMapper().registerKotlinModule()

        /**
         * Load ground unit stats from classpath JSON resource.
         */
        fun load(): GroundUnitStatsRegistry {
            val resource = GroundUnitStatsRegistry::class.java
                .getResourceAsStream("/data/ground_unit_stats.json")
                ?: throw IllegalStateException("ground_unit_stats.json not found on classpath")
            val root: GroundUnitStatsRoot = mapper.readValue(resource)
            return GroundUnitStatsRegistry(root)
        }
    }
}
