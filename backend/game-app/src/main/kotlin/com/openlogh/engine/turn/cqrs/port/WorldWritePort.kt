package com.openlogh.engine.turn.cqrs.port

import com.openlogh.engine.turn.cqrs.memory.DiplomacySnapshot
import com.openlogh.engine.turn.cqrs.memory.FactionSnapshot
import com.openlogh.engine.turn.cqrs.memory.FactionTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.FleetSnapshot
import com.openlogh.engine.turn.cqrs.memory.OfficerSnapshot
import com.openlogh.engine.turn.cqrs.memory.OfficerTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.PlanetSnapshot

interface WorldWritePort {
    fun putOfficer(snapshot: OfficerSnapshot)
    fun putPlanet(snapshot: PlanetSnapshot)
    fun putFaction(snapshot: FactionSnapshot)
    fun putFleet(snapshot: FleetSnapshot)
    fun putDiplomacy(snapshot: DiplomacySnapshot)

    fun deleteOfficer(id: Long)
    fun deletePlanet(id: Long)
    fun deleteFaction(id: Long)
    fun deleteFleet(id: Long)
    fun deleteDiplomacy(id: Long)

    fun setOfficerTurns(officerId: Long, turns: List<OfficerTurnSnapshot>)
    fun setFactionTurns(factionId: Long, officerLevel: Short, turns: List<FactionTurnSnapshot>)
    fun removeOfficerTurns(officerId: Long)
    fun removeFactionTurns(factionId: Long, officerLevel: Short)
}
