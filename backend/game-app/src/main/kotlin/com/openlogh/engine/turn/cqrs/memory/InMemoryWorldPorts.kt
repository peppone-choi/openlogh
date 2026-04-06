package com.openlogh.engine.turn.cqrs.memory

import com.openlogh.engine.turn.cqrs.memory.DirtyTracker.EntityType.OFFICER
import com.openlogh.engine.turn.cqrs.memory.DirtyTracker.EntityType.PLANET
import com.openlogh.engine.turn.cqrs.memory.DirtyTracker.EntityType.DIPLOMACY
import com.openlogh.engine.turn.cqrs.memory.DirtyTracker.EntityType.FACTION
import com.openlogh.engine.turn.cqrs.memory.DirtyTracker.EntityType.FLEET
import com.openlogh.engine.turn.cqrs.memory.DirtyTracker.EntityType.UNIT_CREW
import com.openlogh.engine.turn.cqrs.persist.WorldPorts

class InMemoryWorldPorts(
    private val state: InMemoryWorldState,
    private val dirtyTracker: DirtyTracker,
) : WorldPorts {
    override fun officer(id: Long): OfficerSnapshot? = state.officers[id]

    override fun planet(id: Long): PlanetSnapshot? = state.planets[id]

    override fun faction(id: Long): FactionSnapshot? = state.factions[id]

    override fun fleet(id: Long): FleetSnapshot? = state.fleets[id]

    override fun unitCrew(id: Long): UnitCrewSnapshot? = state.unitCrews[id]

    override fun diplomacy(id: Long): DiplomacySnapshot? = state.diplomacies[id]

    override fun allOfficers(): Collection<OfficerSnapshot> = state.officers.values

    override fun allPlanets(): Collection<PlanetSnapshot> = state.planets.values

    override fun allFactions(): Collection<FactionSnapshot> = state.factions.values

    override fun allFleets(): Collection<FleetSnapshot> = state.fleets.values

    override fun allUnitCrews(): Collection<UnitCrewSnapshot> = state.unitCrews.values

    override fun allDiplomacies(): Collection<DiplomacySnapshot> = state.diplomacies.values

    override fun officersByFaction(factionId: Long): List<OfficerSnapshot> =
        state.officers.values.filter { it.factionId == factionId }

    override fun officersByPlanet(planetId: Long): List<OfficerSnapshot> =
        state.officers.values.filter { it.planetId == planetId }

    override fun planetsByFaction(factionId: Long): List<PlanetSnapshot> =
        state.planets.values.filter { it.factionId == factionId }

    override fun diplomaciesByFaction(factionId: Long): List<DiplomacySnapshot> =
        state.diplomacies.values.filter { it.srcFactionId == factionId || it.destFactionId == factionId }

    override fun activeDiplomacies(): List<DiplomacySnapshot> =
        state.diplomacies.values.filter { !it.isDead }

    override fun officerTurns(officerId: Long): List<OfficerTurnSnapshot> =
        state.officerTurnsByOfficerId[officerId].orEmpty()

    override fun factionTurns(factionId: Long, officerLevel: Short): List<FactionTurnSnapshot> =
        state.factionTurnsByFactionAndLevel[FactionTurnKey(factionId, officerLevel)].orEmpty()

    override fun putOfficer(snapshot: OfficerSnapshot) {
        state.officers[snapshot.id] = snapshot
        dirtyTracker.markDirty(OFFICER, snapshot.id)
    }

    override fun putPlanet(snapshot: PlanetSnapshot) {
        state.planets[snapshot.id] = snapshot
        dirtyTracker.markDirty(PLANET, snapshot.id)
    }

    override fun putFaction(snapshot: FactionSnapshot) {
        state.factions[snapshot.id] = snapshot
        dirtyTracker.markDirty(FACTION, snapshot.id)
    }

    override fun putFleet(snapshot: FleetSnapshot) {
        state.fleets[snapshot.id] = snapshot
        dirtyTracker.markDirty(FLEET, snapshot.id)
    }

    override fun putUnitCrew(snapshot: UnitCrewSnapshot) {
        state.unitCrews[snapshot.id] = snapshot
        dirtyTracker.markDirty(UNIT_CREW, snapshot.id)
    }

    override fun putDiplomacy(snapshot: DiplomacySnapshot) {
        state.diplomacies[snapshot.id] = snapshot
        dirtyTracker.markDirty(DIPLOMACY, snapshot.id)
    }

    override fun deleteOfficer(id: Long) {
        state.officers.remove(id)
        dirtyTracker.markDeleted(OFFICER, id)
    }

    override fun deletePlanet(id: Long) {
        state.planets.remove(id)
        dirtyTracker.markDeleted(PLANET, id)
    }

    override fun deleteFaction(id: Long) {
        state.factions.remove(id)
        dirtyTracker.markDeleted(FACTION, id)
    }

    override fun deleteFleet(id: Long) {
        state.fleets.remove(id)
        dirtyTracker.markDeleted(FLEET, id)
    }

    override fun deleteUnitCrew(id: Long) {
        state.unitCrews.remove(id)
        dirtyTracker.markDeleted(UNIT_CREW, id)
    }

    override fun deleteDiplomacy(id: Long) {
        state.diplomacies.remove(id)
        dirtyTracker.markDeleted(DIPLOMACY, id)
    }

    override fun setOfficerTurns(officerId: Long, turns: List<OfficerTurnSnapshot>) {
        state.officerTurnsByOfficerId[officerId] = turns.toMutableList()
    }

    override fun setFactionTurns(factionId: Long, officerLevel: Short, turns: List<FactionTurnSnapshot>) {
        state.factionTurnsByFactionAndLevel[FactionTurnKey(factionId, officerLevel)] = turns.toMutableList()
    }

    override fun removeOfficerTurns(officerId: Long) {
        state.officerTurnsByOfficerId.remove(officerId)
    }

    override fun removeFactionTurns(factionId: Long, officerLevel: Short) {
        state.factionTurnsByFactionAndLevel.remove(FactionTurnKey(factionId, officerLevel))
    }
}
