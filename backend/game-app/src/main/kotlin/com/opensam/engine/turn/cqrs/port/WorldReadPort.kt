package com.opensam.engine.turn.cqrs.port

import com.opensam.engine.turn.cqrs.memory.CitySnapshot
import com.opensam.engine.turn.cqrs.memory.DiplomacySnapshot
import com.opensam.engine.turn.cqrs.memory.GeneralSnapshot
import com.opensam.engine.turn.cqrs.memory.GeneralTurnSnapshot
import com.opensam.engine.turn.cqrs.memory.NationSnapshot
import com.opensam.engine.turn.cqrs.memory.NationTurnSnapshot
import com.opensam.engine.turn.cqrs.memory.TroopSnapshot

interface WorldReadPort {
    fun general(id: Long): GeneralSnapshot?
    fun city(id: Long): CitySnapshot?
    fun nation(id: Long): NationSnapshot?
    fun troop(id: Long): TroopSnapshot?
    fun diplomacy(id: Long): DiplomacySnapshot?

    fun allGenerals(): Collection<GeneralSnapshot>
    fun allCities(): Collection<CitySnapshot>
    fun allNations(): Collection<NationSnapshot>
    fun allTroops(): Collection<TroopSnapshot>
    fun allDiplomacies(): Collection<DiplomacySnapshot>

    fun generalsByNation(nationId: Long): List<GeneralSnapshot>
    fun generalsByCity(cityId: Long): List<GeneralSnapshot>
    fun citiesByNation(nationId: Long): List<CitySnapshot>
    fun diplomaciesByNation(nationId: Long): List<DiplomacySnapshot>
    fun activeDiplomacies(): List<DiplomacySnapshot>

    fun generalTurns(generalId: Long): List<GeneralTurnSnapshot>
    fun nationTurns(nationId: Long, officerLevel: Short): List<NationTurnSnapshot>
}
