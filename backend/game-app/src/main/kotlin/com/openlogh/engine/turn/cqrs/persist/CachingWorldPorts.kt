package com.openlogh.engine.turn.cqrs.persist

import com.openlogh.engine.turn.cqrs.memory.DiplomacySnapshot
import com.openlogh.engine.turn.cqrs.memory.FactionSnapshot
import com.openlogh.engine.turn.cqrs.memory.FactionTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.FleetSnapshot
import com.openlogh.engine.turn.cqrs.memory.OfficerSnapshot
import com.openlogh.engine.turn.cqrs.memory.OfficerTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.PlanetSnapshot
import com.openlogh.engine.turn.cqrs.memory.UnitCrewSnapshot

class CachingWorldPorts(private val delegate: WorldPorts) : WorldPorts {
    private var officerCache: MutableMap<Long, OfficerSnapshot>? = null
    private var planetCache: MutableMap<Long, PlanetSnapshot>? = null
    private var factionCache: MutableMap<Long, FactionSnapshot>? = null
    private var fleetCache: MutableMap<Long, FleetSnapshot>? = null
    private var unitCrewCache: MutableMap<Long, UnitCrewSnapshot>? = null
    private var diplomacyCache: MutableMap<Long, DiplomacySnapshot>? = null

    override fun allOfficers(): Collection<OfficerSnapshot> = ensureOfficerCache().values

    override fun officer(id: Long): OfficerSnapshot? = ensureOfficerCache()[id]

    override fun officersByFaction(factionId: Long): List<OfficerSnapshot> =
        ensureOfficerCache().values.filter { it.factionId == factionId }

    override fun officersByPlanet(planetId: Long): List<OfficerSnapshot> =
        ensureOfficerCache().values.filter { it.planetId == planetId }

    override fun allPlanets(): Collection<PlanetSnapshot> = ensurePlanetCache().values

    override fun planet(id: Long): PlanetSnapshot? = ensurePlanetCache()[id]

    override fun planetsByFaction(factionId: Long): List<PlanetSnapshot> =
        ensurePlanetCache().values.filter { it.factionId == factionId }

    override fun allFactions(): Collection<FactionSnapshot> = ensureFactionCache().values

    override fun faction(id: Long): FactionSnapshot? = ensureFactionCache()[id]

    override fun allFleets(): Collection<FleetSnapshot> = ensureFleetCache().values

    override fun fleet(id: Long): FleetSnapshot? = ensureFleetCache()[id]

    override fun allUnitCrews(): Collection<UnitCrewSnapshot> = ensureUnitCrewCache().values

    override fun unitCrew(id: Long): UnitCrewSnapshot? = ensureUnitCrewCache()[id]

    override fun allDiplomacies(): Collection<DiplomacySnapshot> = ensureDiplomacyCache().values

    override fun diplomacy(id: Long): DiplomacySnapshot? = ensureDiplomacyCache()[id]

    override fun diplomaciesByFaction(factionId: Long): List<DiplomacySnapshot> =
        ensureDiplomacyCache().values.filter { it.srcFactionId == factionId || it.destFactionId == factionId }

    override fun activeDiplomacies(): List<DiplomacySnapshot> =
        ensureDiplomacyCache().values.filter { !it.isDead }

    override fun officerTurns(officerId: Long): List<OfficerTurnSnapshot> = delegate.officerTurns(officerId)

    override fun factionTurns(factionId: Long, officerLevel: Short): List<FactionTurnSnapshot> =
        delegate.factionTurns(factionId, officerLevel)

    override fun putOfficer(snapshot: OfficerSnapshot) {
        delegate.putOfficer(snapshot)
        officerCache?.put(snapshot.id, snapshot)
    }

    override fun putPlanet(snapshot: PlanetSnapshot) {
        delegate.putPlanet(snapshot)
        planetCache?.put(snapshot.id, snapshot)
    }

    override fun putFaction(snapshot: FactionSnapshot) {
        delegate.putFaction(snapshot)
        factionCache?.put(snapshot.id, snapshot)
    }

    override fun putFleet(snapshot: FleetSnapshot) {
        delegate.putFleet(snapshot)
        fleetCache?.put(snapshot.id, snapshot)
    }

    override fun putUnitCrew(snapshot: UnitCrewSnapshot) {
        delegate.putUnitCrew(snapshot)
        unitCrewCache?.put(snapshot.id, snapshot)
    }

    override fun putDiplomacy(snapshot: DiplomacySnapshot) {
        delegate.putDiplomacy(snapshot)
        diplomacyCache?.put(snapshot.id, snapshot)
    }

    override fun deleteOfficer(id: Long) {
        delegate.deleteOfficer(id)
        officerCache?.remove(id)
    }

    override fun deletePlanet(id: Long) {
        delegate.deletePlanet(id)
        planetCache?.remove(id)
    }

    override fun deleteFaction(id: Long) {
        delegate.deleteFaction(id)
        factionCache?.remove(id)
    }

    override fun deleteFleet(id: Long) {
        delegate.deleteFleet(id)
        fleetCache?.remove(id)
    }

    override fun deleteUnitCrew(id: Long) {
        delegate.deleteUnitCrew(id)
        unitCrewCache?.remove(id)
    }

    override fun deleteDiplomacy(id: Long) {
        delegate.deleteDiplomacy(id)
        diplomacyCache?.remove(id)
    }

    override fun setOfficerTurns(officerId: Long, turns: List<OfficerTurnSnapshot>) =
        delegate.setOfficerTurns(officerId, turns)

    override fun setFactionTurns(factionId: Long, officerLevel: Short, turns: List<FactionTurnSnapshot>) =
        delegate.setFactionTurns(factionId, officerLevel, turns)

    override fun removeOfficerTurns(officerId: Long) = delegate.removeOfficerTurns(officerId)

    override fun removeFactionTurns(factionId: Long, officerLevel: Short) =
        delegate.removeFactionTurns(factionId, officerLevel)

    private fun ensureOfficerCache(): MutableMap<Long, OfficerSnapshot> {
        return officerCache
            ?: delegate.allOfficers().associateByTo(mutableMapOf()) { it.id }.also { officerCache = it }
    }

    private fun ensurePlanetCache(): MutableMap<Long, PlanetSnapshot> {
        return planetCache
            ?: delegate.allPlanets().associateByTo(mutableMapOf()) { it.id }.also { planetCache = it }
    }

    private fun ensureFactionCache(): MutableMap<Long, FactionSnapshot> {
        return factionCache
            ?: delegate.allFactions().associateByTo(mutableMapOf()) { it.id }.also { factionCache = it }
    }

    private fun ensureFleetCache(): MutableMap<Long, FleetSnapshot> {
        return fleetCache
            ?: delegate.allFleets().associateByTo(mutableMapOf()) { it.id }.also { fleetCache = it }
    }

    private fun ensureUnitCrewCache(): MutableMap<Long, UnitCrewSnapshot> {
        return unitCrewCache
            ?: delegate.allUnitCrews().associateByTo(mutableMapOf()) { it.id }.also { unitCrewCache = it }
    }

    private fun ensureDiplomacyCache(): MutableMap<Long, DiplomacySnapshot> {
        return diplomacyCache
            ?: delegate.allDiplomacies().associateByTo(mutableMapOf()) { it.id }.also { diplomacyCache = it }
    }
}
