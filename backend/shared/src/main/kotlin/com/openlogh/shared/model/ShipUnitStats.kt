package com.openlogh.shared.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

/**
 * Ship unit statistics data model.
 * Loads ship stats from JSON resources for combat/production calculations.
 */

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShipStatsData(
    val faction: String = "",
    val factionNameKo: String = "",
    val shipClasses: List<ShipClassData> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShipClassData(
    val classId: String = "",
    val nameKo: String = "",
    val nameEn: String = "",
    val subtypes: List<ShipSubtypeStats> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShipSubtypeStats(
    val subtype: String = "",
    val buildTime: Int = 0,
    val crewPerUnit: Int = 0,
    val output: Int = 0,
    val sensorRange: Int = 0,
    val maxSpeed: Int = 0,
    val armor: ArmorStats = ArmorStats(),
    val shield: ShieldStats = ShieldStats(),
    val beam: WeaponStats = WeaponStats(),
    val gun: WeaponStats = WeaponStats(),
    val missile: WeaponStats = WeaponStats(),
    val antiAir: Int = 0,
    val fighters: Int = 0,
    val cargoCapacity: Int = 0,
    val repairCost: Int = 0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ArmorStats(
    val front: Int = 0,
    val side: Int = 0,
    val rear: Int = 0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShieldStats(
    val protection: Int = 0,
    val capacity: Int = 0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WeaponStats(
    val damage: Int = 0,
    val consumeSupply: Int = 0,
)

/**
 * Registry for looking up ship stats by faction, class, and subtype.
 *
 * Usage:
 * ```
 * val registry = ShipUnitStatsRegistry.load()
 * val stats = registry.lookup("empire", "battleship", "III")
 * ```
 */
class ShipUnitStatsRegistry private constructor(
    private val factionData: Map<String, ShipStatsData>,
) {
    /** Indexed lookup: faction -> classId -> subtype -> stats */
    private val index: Map<String, Map<String, Map<String, ShipSubtypeStats>>> =
        factionData.mapValues { (_, data) ->
            data.shipClasses.associate { shipClass ->
                shipClass.classId to shipClass.subtypes.associateBy { it.subtype }
            }
        }

    /**
     * Look up stats for a specific ship variant.
     * @param faction "empire" or "alliance"
     * @param classId e.g. "battleship", "cruiser", "destroyer"
     * @param subtype e.g. "I", "II", ... "VIII"
     * @return ShipSubtypeStats or null if not found
     */
    fun lookup(faction: String, classId: String, subtype: String): ShipSubtypeStats? =
        index[faction]?.get(classId)?.get(subtype)

    /**
     * Get all ship classes for a faction.
     */
    fun getShipClasses(faction: String): List<ShipClassData> =
        factionData[faction]?.shipClasses ?: emptyList()

    /**
     * Get all subtypes for a faction and class.
     */
    fun getSubtypes(faction: String, classId: String): List<ShipSubtypeStats> =
        factionData[faction]?.shipClasses
            ?.find { it.classId == classId }
            ?.subtypes ?: emptyList()

    /**
     * Get all available faction IDs.
     */
    fun getFactions(): Set<String> = factionData.keys

    /**
     * Get all available class IDs for a faction.
     */
    fun getClassIds(faction: String): Set<String> =
        index[faction]?.keys ?: emptySet()

    companion object {
        private val mapper = ObjectMapper().registerKotlinModule()

        /**
         * Load ship stats from classpath JSON resources.
         */
        fun load(): ShipUnitStatsRegistry {
            val factionData = mutableMapOf<String, ShipStatsData>()

            listOf("empire", "alliance").forEach { faction ->
                val resource = ShipUnitStatsRegistry::class.java
                    .getResourceAsStream("/data/ship_stats_$faction.json")
                if (resource != null) {
                    val data: ShipStatsData = mapper.readValue(resource)
                    factionData[faction] = data
                }
            }

            return ShipUnitStatsRegistry(factionData)
        }
    }
}
