package com.openlogh.engine.turn.cqrs.memory

class DirtyTracker {
    enum class EntityType {
        OFFICER,
        PLANET,
        FACTION,
        FLEET,
        DIPLOMACY,
        UNIT_CREW,
    }

    val dirtyOfficerIds: MutableSet<Long> = mutableSetOf()
    val dirtyPlanetIds: MutableSet<Long> = mutableSetOf()
    val dirtyFactionIds: MutableSet<Long> = mutableSetOf()
    val dirtyFleetIds: MutableSet<Long> = mutableSetOf()
    val dirtyDiplomacyIds: MutableSet<Long> = mutableSetOf()
    val dirtyUnitCrewIds: MutableSet<Long> = mutableSetOf()

    val createdOfficerIds: MutableSet<Long> = mutableSetOf()
    val createdPlanetIds: MutableSet<Long> = mutableSetOf()
    val createdFactionIds: MutableSet<Long> = mutableSetOf()
    val createdFleetIds: MutableSet<Long> = mutableSetOf()
    val createdDiplomacyIds: MutableSet<Long> = mutableSetOf()
    val createdUnitCrewIds: MutableSet<Long> = mutableSetOf()

    val deletedOfficerIds: MutableSet<Long> = mutableSetOf()
    val deletedPlanetIds: MutableSet<Long> = mutableSetOf()
    val deletedFactionIds: MutableSet<Long> = mutableSetOf()
    val deletedFleetIds: MutableSet<Long> = mutableSetOf()
    val deletedDiplomacyIds: MutableSet<Long> = mutableSetOf()
    val deletedUnitCrewIds: MutableSet<Long> = mutableSetOf()

    fun markDirty(type: EntityType, id: Long) {
        when (type) {
            EntityType.OFFICER -> dirtyOfficerIds += id
            EntityType.PLANET -> dirtyPlanetIds += id
            EntityType.FACTION -> dirtyFactionIds += id
            EntityType.FLEET -> dirtyFleetIds += id
            EntityType.DIPLOMACY -> dirtyDiplomacyIds += id
            EntityType.UNIT_CREW -> dirtyUnitCrewIds += id
        }
    }

    fun markCreated(type: EntityType, id: Long) {
        when (type) {
            EntityType.OFFICER -> createdOfficerIds += id
            EntityType.PLANET -> createdPlanetIds += id
            EntityType.FACTION -> createdFactionIds += id
            EntityType.FLEET -> createdFleetIds += id
            EntityType.DIPLOMACY -> createdDiplomacyIds += id
            EntityType.UNIT_CREW -> createdUnitCrewIds += id
        }
    }

    fun markDeleted(type: EntityType, id: Long) {
        when (type) {
            EntityType.OFFICER -> deletedOfficerIds += id
            EntityType.PLANET -> deletedPlanetIds += id
            EntityType.FACTION -> deletedFactionIds += id
            EntityType.FLEET -> deletedFleetIds += id
            EntityType.DIPLOMACY -> deletedDiplomacyIds += id
            EntityType.UNIT_CREW -> deletedUnitCrewIds += id
        }
    }

    fun consumeAll(): DirtyChanges {
        val changes = DirtyChanges(
            dirtyOfficerIds = dirtyOfficerIds.toSet(),
            dirtyPlanetIds = dirtyPlanetIds.toSet(),
            dirtyFactionIds = dirtyFactionIds.toSet(),
            dirtyFleetIds = dirtyFleetIds.toSet(),
            dirtyDiplomacyIds = dirtyDiplomacyIds.toSet(),
            dirtyUnitCrewIds = dirtyUnitCrewIds.toSet(),
            createdOfficerIds = createdOfficerIds.toSet(),
            createdPlanetIds = createdPlanetIds.toSet(),
            createdFactionIds = createdFactionIds.toSet(),
            createdFleetIds = createdFleetIds.toSet(),
            createdDiplomacyIds = createdDiplomacyIds.toSet(),
            createdUnitCrewIds = createdUnitCrewIds.toSet(),
            deletedOfficerIds = deletedOfficerIds.toSet(),
            deletedPlanetIds = deletedPlanetIds.toSet(),
            deletedFactionIds = deletedFactionIds.toSet(),
            deletedFleetIds = deletedFleetIds.toSet(),
            deletedDiplomacyIds = deletedDiplomacyIds.toSet(),
            deletedUnitCrewIds = deletedUnitCrewIds.toSet(),
        )
        clearAll()
        return changes
    }

    private fun clearAll() {
        dirtyOfficerIds.clear()
        dirtyPlanetIds.clear()
        dirtyFactionIds.clear()
        dirtyFleetIds.clear()
        dirtyDiplomacyIds.clear()
        dirtyUnitCrewIds.clear()

        createdOfficerIds.clear()
        createdPlanetIds.clear()
        createdFactionIds.clear()
        createdFleetIds.clear()
        createdDiplomacyIds.clear()
        createdUnitCrewIds.clear()

        deletedOfficerIds.clear()
        deletedPlanetIds.clear()
        deletedFactionIds.clear()
        deletedFleetIds.clear()
        deletedDiplomacyIds.clear()
        deletedUnitCrewIds.clear()
    }
}

data class DirtyChanges(
    val dirtyOfficerIds: Set<Long>,
    val dirtyPlanetIds: Set<Long>,
    val dirtyFactionIds: Set<Long>,
    val dirtyFleetIds: Set<Long>,
    val dirtyDiplomacyIds: Set<Long>,
    val dirtyUnitCrewIds: Set<Long>,
    val createdOfficerIds: Set<Long>,
    val createdPlanetIds: Set<Long>,
    val createdFactionIds: Set<Long>,
    val createdFleetIds: Set<Long>,
    val createdDiplomacyIds: Set<Long>,
    val createdUnitCrewIds: Set<Long>,
    val deletedOfficerIds: Set<Long>,
    val deletedPlanetIds: Set<Long>,
    val deletedFactionIds: Set<Long>,
    val deletedFleetIds: Set<Long>,
    val deletedDiplomacyIds: Set<Long>,
    val deletedUnitCrewIds: Set<Long>,
)
