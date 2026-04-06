package com.openlogh.service

import com.openlogh.dto.TroopMemberInfo
import com.openlogh.dto.TroopWithMembers
import com.openlogh.entity.Fleet
import com.openlogh.model.CrewSlotRole
import com.openlogh.model.UnitType
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.FleetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FleetService(
    private val fleetRepository: FleetRepository,
    private val officerRepository: OfficerRepository,
    private val formationCapService: FormationCapService,
    private val unitCrewService: UnitCrewService,
) {
    fun listByNation(nationId: Long): List<TroopWithMembers> {
        val troops = fleetRepository.findByFactionId(nationId)
        return troops.map { troop ->
            val members = officerRepository.findByFleetId(troop.id)
            TroopWithMembers(troop, members.map { TroopMemberInfo(it.id, it.name, it.picture) })
        }
    }

    fun listByFaction(sessionId: Long, factionId: Long): List<Fleet> {
        return fleetRepository.findBySessionIdAndFactionId(sessionId, factionId)
    }

    fun listByFactionAndType(factionId: Long, unitType: UnitType): List<Fleet> {
        return fleetRepository.findByFactionIdAndUnitType(factionId, unitType.name)
    }

    /**
     * Create a new unit with formation cap validation.
     * @param unitType the type of unit to create (defaults to FLEET for backward compat)
     * @param planetId required for GARRISON type
     */
    @Transactional
    fun create(
        worldId: Long,
        leaderGeneralId: Long,
        nationId: Long,
        name: String,
        unitType: UnitType = UnitType.FLEET,
        planetId: Long? = null,
    ): Fleet {
        // Validate formation cap
        if (unitType.isPopulationLimited) {
            if (!formationCapService.canFormUnit(worldId, nationId, unitType)) {
                throw IllegalArgumentException(
                    "${unitType.description} 편성 한도에 도달했습니다. 인구가 부족합니다."
                )
            }
        }

        // Garrison requires planetId
        if (unitType == UnitType.GARRISON && planetId == null) {
            throw IllegalArgumentException("행성수비대는 행성 ID가 필요합니다.")
        }

        val troop = fleetRepository.save(Fleet(
            sessionId = worldId,
            leaderOfficerId = leaderGeneralId,
            factionId = nationId,
            name = name,
            unitType = unitType.name,
            maxUnits = unitType.maxUnits,
            maxCrew = unitType.maxCrew,
            planetId = planetId,
        ))

        // Update officer's fleetId for backward compat
        officerRepository.findById(leaderGeneralId).ifPresent { gen ->
            gen.fleetId = troop.id
            officerRepository.save(gen)
        }

        // Auto-assign creator as COMMANDER if the unit type has crew slots
        if (unitType.allowedSlotRoles.isNotEmpty()) {
            unitCrewService.assignCrew(troop, leaderGeneralId, CrewSlotRole.COMMANDER)
        }

        return troop
    }

    /**
     * Join a unit with crew slot assignment.
     * @param slotRole the crew slot role (defaults to next available for backward compat)
     */
    @Transactional
    fun join(troopId: Long, generalId: Long, slotRole: CrewSlotRole? = null): Boolean {
        val fleet = fleetRepository.findById(troopId).orElse(null) ?: return false
        val officer = officerRepository.findById(generalId).orElse(null) ?: return false

        // Determine slot role
        val role = slotRole ?: findNextAvailableSlot(fleet) ?: return false

        unitCrewService.assignCrew(fleet, generalId, role)

        // Backward compat: update officer.fleetId
        officer.fleetId = troopId
        officerRepository.save(officer)
        return true
    }

    @Transactional
    fun exit(generalId: Long): Boolean {
        val officer = officerRepository.findById(generalId).orElse(null) ?: return false
        val fleetId = officer.fleetId
        if (fleetId > 0) {
            unitCrewService.removeCrew(fleetId, generalId)
        }
        officer.fleetId = 0
        officerRepository.save(officer)
        return true
    }

    @Transactional
    fun rename(troopId: Long, name: String): Fleet? {
        val troop = fleetRepository.findById(troopId).orElse(null) ?: return null
        troop.name = name
        return fleetRepository.save(troop)
    }

    @Transactional
    fun disband(troopId: Long): Boolean {
        if (!fleetRepository.existsById(troopId)) return false

        // Remove all crew assignments
        unitCrewService.removeAllCrew(troopId)

        // Backward compat: clear officer.fleetId
        val members = officerRepository.findByFleetId(troopId)
        members.forEach { it.fleetId = 0; officerRepository.save(it) }

        fleetRepository.deleteById(troopId)
        return true
    }

    /**
     * Find the next available crew slot for a fleet based on its unit type's allowed roles.
     */
    private fun findNextAvailableSlot(fleet: Fleet): CrewSlotRole? {
        val unitType = fleet.getUnitTypeEnum()
        val existingRoster = unitCrewService.getCrewRoster(fleet.id)
        val filledRoles = existingRoster.map { it.getSlotRoleEnum() }.toSet()
        return unitType.allowedSlotRoles.firstOrNull { it !in filledRoles }
    }
}
