package com.openlogh.engine.turn.cqrs.persist

import com.openlogh.engine.turn.cqrs.memory.CitySnapshot
import com.openlogh.engine.turn.cqrs.memory.DiplomacySnapshot
import com.openlogh.engine.turn.cqrs.memory.GeneralSnapshot
import com.openlogh.engine.turn.cqrs.memory.GeneralTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.NationSnapshot
import com.openlogh.engine.turn.cqrs.memory.NationTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.TroopSnapshot
import com.openlogh.engine.turn.cqrs.port.WorldReadPort
import com.openlogh.engine.turn.cqrs.port.WorldWritePort
import com.openlogh.entity.City
import com.openlogh.entity.Diplomacy
import com.openlogh.entity.General
import com.openlogh.entity.Nation
import com.openlogh.repository.CityRepository
import com.openlogh.repository.DiplomacyRepository
import com.openlogh.repository.GeneralRepository
import com.openlogh.repository.GeneralTurnRepository
import com.openlogh.repository.NationRepository
import com.openlogh.repository.NationTurnRepository
import com.openlogh.repository.TroopRepository
import org.springframework.stereotype.Component

interface WorldPorts : WorldReadPort, WorldWritePort

@Component
class JpaWorldPortFactory(
    private val generalRepository: GeneralRepository? = null,
    private val cityRepository: CityRepository? = null,
    private val nationRepository: NationRepository? = null,
    private val troopRepository: TroopRepository? = null,
    private val diplomacyRepository: DiplomacyRepository? = null,
    private val generalTurnRepository: GeneralTurnRepository? = null,
    private val nationTurnRepository: NationTurnRepository? = null,
) {
    private val scopedPorts = ThreadLocal<MutableMap<Long, CachingWorldPorts>?>()

    fun create(worldId: Long): WorldPorts {
        val activeScope = scopedPorts.get()
        if (activeScope != null) {
            return activeScope.getOrPut(worldId) { CachingWorldPorts(createRaw(worldId)) }
        }

        return createRaw(worldId)
    }

    fun beginScope() {
        if (scopedPorts.get() == null) {
            scopedPorts.set(mutableMapOf())
        }
    }

    fun endScope() {
        scopedPorts.remove()
    }

    private fun createRaw(worldId: Long): WorldPorts {
        if (
            generalRepository != null && cityRepository != null && nationRepository != null &&
            troopRepository != null && diplomacyRepository != null &&
            generalTurnRepository != null && nationTurnRepository != null
        ) {
            return JpaWorldPorts(
                worldId = worldId,
                generalRepository = generalRepository,
                cityRepository = cityRepository,
                nationRepository = nationRepository,
                troopRepository = troopRepository,
                diplomacyRepository = diplomacyRepository,
                generalTurnRepository = generalTurnRepository,
                nationTurnRepository = nationTurnRepository,
            )
        }

        return PartialJpaWorldPorts(
            worldId = worldId,
            generalRepository = generalRepository,
            cityRepository = cityRepository,
            nationRepository = nationRepository,
            diplomacyRepository = diplomacyRepository,
        )
    }
}

private class PartialJpaWorldPorts(
    private val worldId: Long,
    private val generalRepository: GeneralRepository?,
    private val cityRepository: CityRepository?,
    private val nationRepository: NationRepository?,
    private val diplomacyRepository: DiplomacyRepository?,
) : WorldPorts {
    override fun general(id: Long): GeneralSnapshot? =
        generalRepository?.findById(id)?.orElse(null)?.takeIf { it.worldId == worldId }?.toSnapshot()

    override fun city(id: Long): CitySnapshot? =
        cityRepository?.findById(id)?.orElse(null)?.takeIf { it.worldId == worldId }?.toSnapshot()

    override fun nation(id: Long): NationSnapshot? =
        nationRepository?.findById(id)?.orElse(null)?.takeIf { it.worldId == worldId }?.toSnapshot()

    override fun troop(id: Long): TroopSnapshot? = null

    override fun diplomacy(id: Long): DiplomacySnapshot? =
        diplomacyRepository?.findById(id)?.orElse(null)?.takeIf { it.worldId == worldId }?.toSnapshot()

    override fun allGenerals(): Collection<GeneralSnapshot> = generalRepository?.findByWorldId(worldId)?.map(General::toSnapshot).orEmpty()

    override fun allCities(): Collection<CitySnapshot> = cityRepository?.findByWorldId(worldId)?.map(City::toSnapshot).orEmpty()

    override fun allNations(): Collection<NationSnapshot> = nationRepository?.findByWorldId(worldId)?.map(Nation::toSnapshot).orEmpty()

    override fun allTroops(): Collection<TroopSnapshot> = emptyList()

    override fun allDiplomacies(): Collection<DiplomacySnapshot> =
        diplomacyRepository?.findByWorldId(worldId)?.map(Diplomacy::toSnapshot).orEmpty()

    override fun generalsByNation(nationId: Long): List<GeneralSnapshot> =
        generalRepository?.findByWorldIdAndNationId(worldId, nationId)?.map(General::toSnapshot).orEmpty()

    override fun generalsByCity(cityId: Long): List<GeneralSnapshot> =
        generalRepository?.findByCityId(cityId)?.filter { it.worldId == worldId }?.map(General::toSnapshot).orEmpty()

    override fun citiesByNation(nationId: Long): List<CitySnapshot> =
        cityRepository?.findByNationId(nationId)?.filter { it.worldId == worldId }?.map(City::toSnapshot).orEmpty()

    override fun diplomaciesByNation(nationId: Long): List<DiplomacySnapshot> =
        diplomacyRepository
            ?.findByWorldIdAndSrcNationIdOrDestNationId(worldId, nationId, nationId)
            ?.filter { it.worldId == worldId }
            ?.map(Diplomacy::toSnapshot)
            .orEmpty()

    override fun activeDiplomacies(): List<DiplomacySnapshot> =
        diplomacyRepository?.findByWorldIdAndIsDeadFalse(worldId)?.map(Diplomacy::toSnapshot).orEmpty()

    override fun generalTurns(generalId: Long): List<GeneralTurnSnapshot> = emptyList()

    override fun nationTurns(nationId: Long, officerLevel: Short): List<NationTurnSnapshot> = emptyList()

    override fun putGeneral(snapshot: GeneralSnapshot) {
        checkNotNull(generalRepository) { "GeneralRepository is required" }
        generalRepository.save(snapshot.toEntity())
    }

    override fun putCity(snapshot: CitySnapshot) {
        checkNotNull(cityRepository) { "CityRepository is required" }
        cityRepository.save(snapshot.toEntity())
    }

    override fun putNation(snapshot: NationSnapshot) {
        checkNotNull(nationRepository) { "NationRepository is required" }
        nationRepository.save(snapshot.toEntity())
    }

    override fun putTroop(snapshot: TroopSnapshot) {
        throw UnsupportedOperationException("Troop operations are not available in PartialJpaWorldPorts")
    }

    override fun putDiplomacy(snapshot: DiplomacySnapshot) {
        checkNotNull(diplomacyRepository) { "DiplomacyRepository is required" }
        diplomacyRepository.save(snapshot.toEntity())
    }

    override fun deleteGeneral(id: Long) {
        checkNotNull(generalRepository) { "GeneralRepository is required" }
        generalRepository.deleteById(id)
    }

    override fun deleteCity(id: Long) {
        checkNotNull(cityRepository) { "CityRepository is required" }
        cityRepository.deleteById(id)
    }

    override fun deleteNation(id: Long) {
        checkNotNull(nationRepository) { "NationRepository is required" }
        nationRepository.deleteById(id)
    }

    override fun deleteTroop(id: Long) {
        throw UnsupportedOperationException("Troop operations are not available in PartialJpaWorldPorts")
    }

    override fun deleteDiplomacy(id: Long) {
        checkNotNull(diplomacyRepository) { "DiplomacyRepository is required" }
        diplomacyRepository.deleteById(id)
    }

    override fun setGeneralTurns(generalId: Long, turns: List<GeneralTurnSnapshot>) {
        throw UnsupportedOperationException("Turn operations are not available in PartialJpaWorldPorts")
    }

    override fun setNationTurns(nationId: Long, officerLevel: Short, turns: List<NationTurnSnapshot>) {
        throw UnsupportedOperationException("Turn operations are not available in PartialJpaWorldPorts")
    }

    override fun removeGeneralTurns(generalId: Long) {
        throw UnsupportedOperationException("Turn operations are not available in PartialJpaWorldPorts")
    }

    override fun removeNationTurns(nationId: Long, officerLevel: Short) {
        throw UnsupportedOperationException("Turn operations are not available in PartialJpaWorldPorts")
    }
}
