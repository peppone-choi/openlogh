package com.openlogh.engine.turn.cqrs.persist

import com.openlogh.engine.turn.cqrs.memory.CitySnapshot
import com.openlogh.engine.turn.cqrs.memory.DiplomacySnapshot
import com.openlogh.engine.turn.cqrs.memory.GeneralSnapshot
import com.openlogh.engine.turn.cqrs.memory.GeneralTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.NationSnapshot
import com.openlogh.engine.turn.cqrs.memory.NationTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.TroopSnapshot

class CachingWorldPorts(private val delegate: WorldPorts) : WorldPorts {
    private var generalCache: MutableMap<Long, GeneralSnapshot>? = null
    private var cityCache: MutableMap<Long, CitySnapshot>? = null
    private var nationCache: MutableMap<Long, NationSnapshot>? = null
    private var troopCache: MutableMap<Long, TroopSnapshot>? = null
    private var diplomacyCache: MutableMap<Long, DiplomacySnapshot>? = null

    override fun allGenerals(): Collection<GeneralSnapshot> = ensureGeneralCache().values

    override fun general(id: Long): GeneralSnapshot? = ensureGeneralCache()[id]

    override fun generalsByNation(nationId: Long): List<GeneralSnapshot> =
        ensureGeneralCache().values.filter { it.nationId == nationId }

    override fun generalsByCity(cityId: Long): List<GeneralSnapshot> =
        ensureGeneralCache().values.filter { it.cityId == cityId }

    override fun allCities(): Collection<CitySnapshot> = ensureCityCache().values

    override fun city(id: Long): CitySnapshot? = ensureCityCache()[id]

    override fun citiesByNation(nationId: Long): List<CitySnapshot> =
        ensureCityCache().values.filter { it.nationId == nationId }

    override fun allNations(): Collection<NationSnapshot> = ensureNationCache().values

    override fun nation(id: Long): NationSnapshot? = ensureNationCache()[id]

    override fun allTroops(): Collection<TroopSnapshot> = ensureTroopCache().values

    override fun troop(id: Long): TroopSnapshot? = ensureTroopCache()[id]

    override fun allDiplomacies(): Collection<DiplomacySnapshot> = ensureDiplomacyCache().values

    override fun diplomacy(id: Long): DiplomacySnapshot? = ensureDiplomacyCache()[id]

    override fun diplomaciesByNation(nationId: Long): List<DiplomacySnapshot> =
        ensureDiplomacyCache().values.filter { it.srcNationId == nationId || it.destNationId == nationId }

    override fun activeDiplomacies(): List<DiplomacySnapshot> =
        ensureDiplomacyCache().values.filter { !it.isDead }

    override fun generalTurns(generalId: Long): List<GeneralTurnSnapshot> = delegate.generalTurns(generalId)

    override fun nationTurns(nationId: Long, officerLevel: Short): List<NationTurnSnapshot> =
        delegate.nationTurns(nationId, officerLevel)

    override fun putGeneral(snapshot: GeneralSnapshot) {
        delegate.putGeneral(snapshot)
        generalCache?.put(snapshot.id, snapshot)
    }

    override fun putCity(snapshot: CitySnapshot) {
        delegate.putCity(snapshot)
        cityCache?.put(snapshot.id, snapshot)
    }

    override fun putNation(snapshot: NationSnapshot) {
        delegate.putNation(snapshot)
        nationCache?.put(snapshot.id, snapshot)
    }

    override fun putTroop(snapshot: TroopSnapshot) {
        delegate.putTroop(snapshot)
        troopCache?.put(snapshot.id, snapshot)
    }

    override fun putDiplomacy(snapshot: DiplomacySnapshot) {
        delegate.putDiplomacy(snapshot)
        diplomacyCache?.put(snapshot.id, snapshot)
    }

    override fun deleteGeneral(id: Long) {
        delegate.deleteGeneral(id)
        generalCache?.remove(id)
    }

    override fun deleteCity(id: Long) {
        delegate.deleteCity(id)
        cityCache?.remove(id)
    }

    override fun deleteNation(id: Long) {
        delegate.deleteNation(id)
        nationCache?.remove(id)
    }

    override fun deleteTroop(id: Long) {
        delegate.deleteTroop(id)
        troopCache?.remove(id)
    }

    override fun deleteDiplomacy(id: Long) {
        delegate.deleteDiplomacy(id)
        diplomacyCache?.remove(id)
    }

    override fun setGeneralTurns(generalId: Long, turns: List<GeneralTurnSnapshot>) =
        delegate.setGeneralTurns(generalId, turns)

    override fun setNationTurns(nationId: Long, officerLevel: Short, turns: List<NationTurnSnapshot>) =
        delegate.setNationTurns(nationId, officerLevel, turns)

    override fun removeGeneralTurns(generalId: Long) = delegate.removeGeneralTurns(generalId)

    override fun removeNationTurns(nationId: Long, officerLevel: Short) =
        delegate.removeNationTurns(nationId, officerLevel)

    private fun ensureGeneralCache(): MutableMap<Long, GeneralSnapshot> {
        return generalCache
            ?: delegate.allGenerals().associateByTo(mutableMapOf()) { it.id }.also { generalCache = it }
    }

    private fun ensureCityCache(): MutableMap<Long, CitySnapshot> {
        return cityCache
            ?: delegate.allCities().associateByTo(mutableMapOf()) { it.id }.also { cityCache = it }
    }

    private fun ensureNationCache(): MutableMap<Long, NationSnapshot> {
        return nationCache
            ?: delegate.allNations().associateByTo(mutableMapOf()) { it.id }.also { nationCache = it }
    }

    private fun ensureTroopCache(): MutableMap<Long, TroopSnapshot> {
        return troopCache
            ?: delegate.allTroops().associateByTo(mutableMapOf()) { it.id }.also { troopCache = it }
    }

    private fun ensureDiplomacyCache(): MutableMap<Long, DiplomacySnapshot> {
        return diplomacyCache
            ?: delegate.allDiplomacies().associateByTo(mutableMapOf()) { it.id }.also { diplomacyCache = it }
    }
}
