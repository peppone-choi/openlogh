package com.openlogh.engine.turn.cqrs.memory

import com.openlogh.engine.turn.cqrs.memory.DirtyTracker.EntityType.CITY
import com.openlogh.engine.turn.cqrs.memory.DirtyTracker.EntityType.DIPLOMACY
import com.openlogh.engine.turn.cqrs.memory.DirtyTracker.EntityType.GENERAL
import com.openlogh.engine.turn.cqrs.memory.DirtyTracker.EntityType.NATION
import com.openlogh.engine.turn.cqrs.memory.DirtyTracker.EntityType.TROOP
import com.openlogh.engine.turn.cqrs.persist.WorldPorts

class InMemoryWorldPorts(
    private val state: InMemoryWorldState,
    private val dirtyTracker: DirtyTracker,
) : WorldPorts {
    override fun general(id: Long): GeneralSnapshot? = state.generals[id]

    override fun city(id: Long): CitySnapshot? = state.cities[id]

    override fun nation(id: Long): NationSnapshot? = state.nations[id]

    override fun troop(id: Long): TroopSnapshot? = state.troops[id]

    override fun diplomacy(id: Long): DiplomacySnapshot? = state.diplomacies[id]

    override fun allGenerals(): Collection<GeneralSnapshot> = state.generals.values

    override fun allCities(): Collection<CitySnapshot> = state.cities.values

    override fun allNations(): Collection<NationSnapshot> = state.nations.values

    override fun allTroops(): Collection<TroopSnapshot> = state.troops.values

    override fun allDiplomacies(): Collection<DiplomacySnapshot> = state.diplomacies.values

    override fun generalsByNation(nationId: Long): List<GeneralSnapshot> =
        state.generals.values.filter { it.nationId == nationId }

    override fun generalsByCity(cityId: Long): List<GeneralSnapshot> =
        state.generals.values.filter { it.cityId == cityId }

    override fun citiesByNation(nationId: Long): List<CitySnapshot> =
        state.cities.values.filter { it.nationId == nationId }

    override fun diplomaciesByNation(nationId: Long): List<DiplomacySnapshot> =
        state.diplomacies.values.filter { it.srcNationId == nationId || it.destNationId == nationId }

    override fun activeDiplomacies(): List<DiplomacySnapshot> =
        state.diplomacies.values.filter { !it.isDead }

    override fun generalTurns(generalId: Long): List<GeneralTurnSnapshot> =
        state.generalTurnsByGeneralId[generalId].orEmpty()

    override fun nationTurns(nationId: Long, officerLevel: Short): List<NationTurnSnapshot> =
        state.nationTurnsByNationAndLevel[NationTurnKey(nationId, officerLevel)].orEmpty()

    override fun putGeneral(snapshot: GeneralSnapshot) {
        state.generals[snapshot.id] = snapshot
        dirtyTracker.markDirty(GENERAL, snapshot.id)
    }

    override fun putCity(snapshot: CitySnapshot) {
        state.cities[snapshot.id] = snapshot
        dirtyTracker.markDirty(CITY, snapshot.id)
    }

    override fun putNation(snapshot: NationSnapshot) {
        state.nations[snapshot.id] = snapshot
        dirtyTracker.markDirty(NATION, snapshot.id)
    }

    override fun putTroop(snapshot: TroopSnapshot) {
        state.troops[snapshot.id] = snapshot
        dirtyTracker.markDirty(TROOP, snapshot.id)
    }

    override fun putDiplomacy(snapshot: DiplomacySnapshot) {
        state.diplomacies[snapshot.id] = snapshot
        dirtyTracker.markDirty(DIPLOMACY, snapshot.id)
    }

    override fun deleteGeneral(id: Long) {
        state.generals.remove(id)
        dirtyTracker.markDeleted(GENERAL, id)
    }

    override fun deleteCity(id: Long) {
        state.cities.remove(id)
        dirtyTracker.markDeleted(CITY, id)
    }

    override fun deleteNation(id: Long) {
        state.nations.remove(id)
        dirtyTracker.markDeleted(NATION, id)
    }

    override fun deleteTroop(id: Long) {
        state.troops.remove(id)
        dirtyTracker.markDeleted(TROOP, id)
    }

    override fun deleteDiplomacy(id: Long) {
        state.diplomacies.remove(id)
        dirtyTracker.markDeleted(DIPLOMACY, id)
    }

    override fun setGeneralTurns(generalId: Long, turns: List<GeneralTurnSnapshot>) {
        state.generalTurnsByGeneralId[generalId] = turns.toMutableList()
    }

    override fun setNationTurns(nationId: Long, officerLevel: Short, turns: List<NationTurnSnapshot>) {
        state.nationTurnsByNationAndLevel[NationTurnKey(nationId, officerLevel)] = turns.toMutableList()
    }

    override fun removeGeneralTurns(generalId: Long) {
        state.generalTurnsByGeneralId.remove(generalId)
    }

    override fun removeNationTurns(nationId: Long, officerLevel: Short) {
        state.nationTurnsByNationAndLevel.remove(NationTurnKey(nationId, officerLevel))
    }
}
