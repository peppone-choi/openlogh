package com.openlogh.engine.turn.cqrs.persist

import com.openlogh.engine.turn.cqrs.memory.DiplomacySnapshot
import com.openlogh.engine.turn.cqrs.memory.FactionSnapshot
import com.openlogh.engine.turn.cqrs.memory.FactionTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.FleetSnapshot
import com.openlogh.engine.turn.cqrs.memory.OfficerSnapshot
import com.openlogh.engine.turn.cqrs.memory.OfficerTurnSnapshot
import com.openlogh.engine.turn.cqrs.memory.PlanetSnapshot
import com.openlogh.engine.turn.cqrs.memory.UnitCrewSnapshot
import com.openlogh.engine.turn.cqrs.port.WorldReadPort
import com.openlogh.engine.turn.cqrs.port.WorldWritePort
import com.openlogh.entity.Diplomacy
import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import com.openlogh.entity.Planet
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.DiplomacyRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.OfficerTurnRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.FactionTurnRepository
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.UnitCrewRepository
import org.springframework.stereotype.Component

interface WorldPorts : WorldReadPort, WorldWritePort

@Component
class JpaWorldPortFactory(
    private val officerRepository: OfficerRepository? = null,
    private val planetRepository: PlanetRepository? = null,
    private val factionRepository: FactionRepository? = null,
    private val fleetRepository: FleetRepository? = null,
    private val unitCrewRepository: UnitCrewRepository? = null,
    private val diplomacyRepository: DiplomacyRepository? = null,
    private val officerTurnRepository: OfficerTurnRepository? = null,
    private val factionTurnRepository: FactionTurnRepository? = null,
) {
    private val scopedPorts = ThreadLocal<MutableMap<Long, CachingWorldPorts>?>()

    fun create(sessionId: Long): WorldPorts {
        val activeScope = scopedPorts.get()
        if (activeScope != null) {
            return activeScope.getOrPut(sessionId) { CachingWorldPorts(createRaw(sessionId)) }
        }

        return createRaw(sessionId)
    }

    fun beginScope() {
        if (scopedPorts.get() == null) {
            scopedPorts.set(mutableMapOf())
        }
    }

    fun endScope() {
        scopedPorts.remove()
    }

    private fun createRaw(sessionId: Long): WorldPorts {
        if (
            officerRepository != null && planetRepository != null && factionRepository != null &&
            fleetRepository != null && unitCrewRepository != null && diplomacyRepository != null &&
            officerTurnRepository != null && factionTurnRepository != null
        ) {
            return JpaWorldPorts(
                sessionId = sessionId,
                officerRepository = officerRepository,
                planetRepository = planetRepository,
                factionRepository = factionRepository,
                fleetRepository = fleetRepository,
                unitCrewRepository = unitCrewRepository,
                diplomacyRepository = diplomacyRepository,
                officerTurnRepository = officerTurnRepository,
                factionTurnRepository = factionTurnRepository,
            )
        }

        return PartialJpaWorldPorts(
            sessionId = sessionId,
            officerRepository = officerRepository,
            planetRepository = planetRepository,
            factionRepository = factionRepository,
            diplomacyRepository = diplomacyRepository,
        )
    }
}

private class PartialJpaWorldPorts(
    private val sessionId: Long,
    private val officerRepository: OfficerRepository?,
    private val planetRepository: PlanetRepository?,
    private val factionRepository: FactionRepository?,
    private val diplomacyRepository: DiplomacyRepository?,
) : WorldPorts {
    override fun officer(id: Long): OfficerSnapshot? =
        officerRepository?.findById(id)?.orElse(null)?.takeIf { it.sessionId == sessionId }?.toSnapshot()

    override fun planet(id: Long): PlanetSnapshot? =
        planetRepository?.findById(id)?.orElse(null)?.takeIf { it.sessionId == sessionId }?.toSnapshot()

    override fun faction(id: Long): FactionSnapshot? =
        factionRepository?.findById(id)?.orElse(null)?.takeIf { it.sessionId == sessionId }?.toSnapshot()

    override fun fleet(id: Long): FleetSnapshot? = null

    override fun unitCrew(id: Long): UnitCrewSnapshot? = null

    override fun diplomacy(id: Long): DiplomacySnapshot? =
        diplomacyRepository?.findById(id)?.orElse(null)?.takeIf { it.sessionId == sessionId }?.toSnapshot()

    override fun allOfficers(): Collection<OfficerSnapshot> = officerRepository?.findBySessionId(sessionId)?.map(Officer::toSnapshot).orEmpty()

    override fun allPlanets(): Collection<PlanetSnapshot> = planetRepository?.findBySessionId(sessionId)?.map(Planet::toSnapshot).orEmpty()

    override fun allFactions(): Collection<FactionSnapshot> = factionRepository?.findBySessionId(sessionId)?.map(Faction::toSnapshot).orEmpty()

    override fun allFleets(): Collection<FleetSnapshot> = emptyList()

    override fun allUnitCrews(): Collection<UnitCrewSnapshot> = emptyList()

    override fun allDiplomacies(): Collection<DiplomacySnapshot> =
        diplomacyRepository?.findBySessionId(sessionId)?.map(Diplomacy::toSnapshot).orEmpty()

    override fun officersByFaction(factionId: Long): List<OfficerSnapshot> =
        officerRepository?.findBySessionIdAndFactionId(sessionId, factionId)?.map(Officer::toSnapshot).orEmpty()

    override fun officersByPlanet(planetId: Long): List<OfficerSnapshot> =
        officerRepository?.findByPlanetId(planetId)?.filter { it.sessionId == sessionId }?.map(Officer::toSnapshot).orEmpty()

    override fun planetsByFaction(factionId: Long): List<PlanetSnapshot> =
        planetRepository?.findByFactionId(factionId)?.filter { it.sessionId == sessionId }?.map(Planet::toSnapshot).orEmpty()

    override fun diplomaciesByFaction(factionId: Long): List<DiplomacySnapshot> =
        diplomacyRepository
            ?.findBySessionIdAndSrcFactionIdOrDestFactionId(sessionId, factionId, factionId)
            ?.filter { it.sessionId == sessionId }
            ?.map(Diplomacy::toSnapshot)
            .orEmpty()

    override fun activeDiplomacies(): List<DiplomacySnapshot> =
        diplomacyRepository?.findBySessionIdAndIsDeadFalse(sessionId)?.map(Diplomacy::toSnapshot).orEmpty()

    override fun officerTurns(officerId: Long): List<OfficerTurnSnapshot> = emptyList()

    override fun factionTurns(factionId: Long, officerLevel: Short): List<FactionTurnSnapshot> = emptyList()

    override fun putOfficer(snapshot: OfficerSnapshot) {
        checkNotNull(officerRepository) { "OfficerRepository is required" }
        officerRepository.save(snapshot.toEntity())
    }

    override fun putPlanet(snapshot: PlanetSnapshot) {
        checkNotNull(planetRepository) { "PlanetRepository is required" }
        planetRepository.save(snapshot.toEntity())
    }

    override fun putFaction(snapshot: FactionSnapshot) {
        checkNotNull(factionRepository) { "FactionRepository is required" }
        factionRepository.save(snapshot.toEntity())
    }

    override fun putFleet(snapshot: FleetSnapshot) {
        throw UnsupportedOperationException("Fleet operations are not available in PartialJpaWorldPorts")
    }

    override fun putUnitCrew(snapshot: UnitCrewSnapshot) {
        throw UnsupportedOperationException("UnitCrew operations are not available in PartialJpaWorldPorts")
    }

    override fun putDiplomacy(snapshot: DiplomacySnapshot) {
        checkNotNull(diplomacyRepository) { "DiplomacyRepository is required" }
        diplomacyRepository.save(snapshot.toEntity())
    }

    override fun deleteOfficer(id: Long) {
        checkNotNull(officerRepository) { "OfficerRepository is required" }
        officerRepository.deleteById(id)
    }

    override fun deletePlanet(id: Long) {
        checkNotNull(planetRepository) { "PlanetRepository is required" }
        planetRepository.deleteById(id)
    }

    override fun deleteFaction(id: Long) {
        checkNotNull(factionRepository) { "FactionRepository is required" }
        factionRepository.deleteById(id)
    }

    override fun deleteFleet(id: Long) {
        throw UnsupportedOperationException("Fleet operations are not available in PartialJpaWorldPorts")
    }

    override fun deleteUnitCrew(id: Long) {
        throw UnsupportedOperationException("UnitCrew operations are not available in PartialJpaWorldPorts")
    }

    override fun deleteDiplomacy(id: Long) {
        checkNotNull(diplomacyRepository) { "DiplomacyRepository is required" }
        diplomacyRepository.deleteById(id)
    }

    override fun setOfficerTurns(officerId: Long, turns: List<OfficerTurnSnapshot>) {
        throw UnsupportedOperationException("Turn operations are not available in PartialJpaWorldPorts")
    }

    override fun setFactionTurns(factionId: Long, officerLevel: Short, turns: List<FactionTurnSnapshot>) {
        throw UnsupportedOperationException("Turn operations are not available in PartialJpaWorldPorts")
    }

    override fun removeOfficerTurns(officerId: Long) {
        throw UnsupportedOperationException("Turn operations are not available in PartialJpaWorldPorts")
    }

    override fun removeFactionTurns(factionId: Long, officerLevel: Short) {
        throw UnsupportedOperationException("Turn operations are not available in PartialJpaWorldPorts")
    }
}
