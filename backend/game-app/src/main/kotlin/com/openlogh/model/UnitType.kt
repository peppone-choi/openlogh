package com.openlogh.model

/**
 * The 6 unit types from gin7 manual.
 *
 * Each type defines its composition constants:
 * - maxUnits: maximum number of ship units (1 unit = [shipsPerUnit] ships)
 * - maxShips: total ship capacity (maxUnits * shipsPerUnit)
 * - maxCrew: maximum number of officer crew slots
 * - shipsPerUnit: ships per single unit (300 for combat types)
 */
enum class UnitType(
    val maxUnits: Int,
    val maxShips: Int,
    val maxCrew: Int,
    val shipsPerUnit: Int,
    val description: String,
) {
    FLEET(60, 18_000, 10, 300, "함대"),
    PATROL(3, 900, 3, 300, "순찰대"),
    TRANSPORT(23, 6_900, 3, 300, "수송함대"),
    GROUND(6, 1_800, 1, 300, "지상부대"),
    GARRISON(10, 0, 1, 0, "행성수비대"),
    SOLO(0, 1, 0, 0, "단독함");

    /** Population cost: how many people per 1 unit of this type */
    val populationPerUnit: Long
        get() = when (this) {
            FLEET, TRANSPORT -> 1_000_000_000L
            PATROL, GROUND -> 1_000_000_000L / 6
            GARRISON, SOLO -> 0L
        }

    /** Whether this type is limited by faction population */
    val isPopulationLimited: Boolean
        get() = this in listOf(FLEET, TRANSPORT, PATROL, GROUND)

    /** Allowed CrewSlotRoles for this unit type */
    val allowedSlotRoles: List<CrewSlotRole>
        get() = when (this) {
            FLEET -> listOf(
                CrewSlotRole.COMMANDER, CrewSlotRole.VICE_COMMANDER,
                CrewSlotRole.CHIEF_OF_STAFF,
                CrewSlotRole.STAFF_OFFICER_1, CrewSlotRole.STAFF_OFFICER_2,
                CrewSlotRole.STAFF_OFFICER_3, CrewSlotRole.STAFF_OFFICER_4,
                CrewSlotRole.STAFF_OFFICER_5, CrewSlotRole.STAFF_OFFICER_6,
                CrewSlotRole.ADJUTANT,
            )
            PATROL, TRANSPORT -> listOf(
                CrewSlotRole.COMMANDER, CrewSlotRole.VICE_COMMANDER, CrewSlotRole.ADJUTANT,
            )
            GROUND, GARRISON -> listOf(CrewSlotRole.COMMANDER)
            SOLO -> emptyList()
        }
}
