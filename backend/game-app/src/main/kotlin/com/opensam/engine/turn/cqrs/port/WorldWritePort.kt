package com.opensam.engine.turn.cqrs.port

import com.opensam.engine.turn.cqrs.memory.CitySnapshot
import com.opensam.engine.turn.cqrs.memory.DiplomacySnapshot
import com.opensam.engine.turn.cqrs.memory.GeneralSnapshot
import com.opensam.engine.turn.cqrs.memory.GeneralTurnSnapshot
import com.opensam.engine.turn.cqrs.memory.NationSnapshot
import com.opensam.engine.turn.cqrs.memory.NationTurnSnapshot
import com.opensam.engine.turn.cqrs.memory.TroopSnapshot

interface WorldWritePort {
    fun putGeneral(snapshot: GeneralSnapshot)
    fun putCity(snapshot: CitySnapshot)
    fun putNation(snapshot: NationSnapshot)
    fun putTroop(snapshot: TroopSnapshot)
    fun putDiplomacy(snapshot: DiplomacySnapshot)

    fun deleteGeneral(id: Long)
    fun deleteCity(id: Long)
    fun deleteNation(id: Long)
    fun deleteTroop(id: Long)
    fun deleteDiplomacy(id: Long)

    fun setGeneralTurns(generalId: Long, turns: List<GeneralTurnSnapshot>)
    fun setNationTurns(nationId: Long, officerLevel: Short, turns: List<NationTurnSnapshot>)
    fun removeGeneralTurns(generalId: Long)
    fun removeNationTurns(nationId: Long, officerLevel: Short)
}
