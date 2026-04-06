package com.openlogh.engine.turn.cqrs.port

import com.openlogh.engine.turn.cqrs.memory.DiplomacySnapshot
import com.openlogh.engine.turn.cqrs.memory.FactionSnapshot
import com.openlogh.engine.turn.cqrs.memory.FactionTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.FleetSnapshot
import com.openlogh.engine.turn.cqrs.memory.OfficerSnapshot
import com.openlogh.engine.turn.cqrs.memory.OfficerTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.PlanetSnapshot
import com.openlogh.engine.turn.cqrs.memory.UnitCrewSnapshot

interface WorldReadPort {
    fun officer(id: Long): OfficerSnapshot?
    fun planet(id: Long): PlanetSnapshot?
    fun faction(id: Long): FactionSnapshot?
    fun fleet(id: Long): FleetSnapshot?
    fun unitCrew(id: Long): UnitCrewSnapshot?
    fun diplomacy(id: Long): DiplomacySnapshot?

    fun allOfficers(): Collection<OfficerSnapshot>
    fun allPlanets(): Collection<PlanetSnapshot>
    fun allFactions(): Collection<FactionSnapshot>
    fun allFleets(): Collection<FleetSnapshot>
    fun allUnitCrews(): Collection<UnitCrewSnapshot>
    fun allDiplomacies(): Collection<DiplomacySnapshot>

    fun officersByFaction(factionId: Long): List<OfficerSnapshot>
    fun officersByPlanet(planetId: Long): List<OfficerSnapshot>
    fun planetsByFaction(factionId: Long): List<PlanetSnapshot>
    fun diplomaciesByFaction(factionId: Long): List<DiplomacySnapshot>
    fun activeDiplomacies(): List<DiplomacySnapshot>

    fun officerTurns(officerId: Long): List<OfficerTurnSnapshot>
    fun factionTurns(factionId: Long, officerLevel: Short): List<FactionTurnSnapshot>
}
