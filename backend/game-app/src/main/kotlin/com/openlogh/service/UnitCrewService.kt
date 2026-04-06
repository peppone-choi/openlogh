package com.openlogh.service

import com.openlogh.entity.Fleet
import com.openlogh.entity.UnitCrew
import com.openlogh.model.CrewSlotRole
import com.openlogh.repository.UnitCrewRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Manages crew slot assignments for fleet units.
 * Validates slot roles against unit type constraints from gin7 manual.
 */
@Service
class UnitCrewService(
    private val unitCrewRepository: UnitCrewRepository,
) {
    /**
     * Assign an officer to a crew slot on a unit.
     * @throws IllegalArgumentException if slot role is invalid, crew is full, or officer already assigned
     */
    @Transactional
    fun assignCrew(fleet: Fleet, officerId: Long, slotRole: CrewSlotRole): UnitCrew {
        validateSlotAssignment(fleet, slotRole)

        // Check officer is not already assigned to another unit
        val existing = unitCrewRepository.findByOfficerId(officerId)
        if (existing.isNotEmpty()) {
            throw IllegalArgumentException("해당 장교는 이미 다른 부대에 배속되어 있습니다 (officerId=$officerId)")
        }

        val crew = UnitCrew().apply {
            this.sessionId = fleet.sessionId
            this.fleetId = fleet.id
            this.officerId = officerId
            this.slotRole = slotRole.name
        }
        return unitCrewRepository.save(crew)
    }

    /**
     * Remove an officer from a unit's crew.
     */
    @Transactional
    fun removeCrew(fleetId: Long, officerId: Long) {
        unitCrewRepository.deleteByFleetIdAndOfficerId(fleetId, officerId)
    }

    /**
     * Remove all crew assignments for a unit (used when disbanding).
     */
    @Transactional
    fun removeAllCrew(fleetId: Long) {
        val crew = unitCrewRepository.findByFleetId(fleetId)
        unitCrewRepository.deleteAll(crew)
    }

    /**
     * Get all crew members of a unit.
     */
    fun getCrewRoster(fleetId: Long): List<UnitCrew> {
        return unitCrewRepository.findByFleetId(fleetId)
    }

    /**
     * Validate that a slot role assignment is allowed for the given fleet.
     * @throws IllegalArgumentException if validation fails
     */
    fun validateSlotAssignment(fleet: Fleet, slotRole: CrewSlotRole) {
        val unitType = fleet.getUnitTypeEnum()
        val allowedRoles = unitType.allowedSlotRoles

        if (slotRole !in allowedRoles) {
            throw IllegalArgumentException(
                "${unitType.description}에는 ${slotRole.displayNameKo} 슬롯이 허용되지 않습니다"
            )
        }

        // Check crew count limit
        val currentCount = unitCrewRepository.countByFleetId(fleet.id)
        if (currentCount >= fleet.maxCrew) {
            throw IllegalArgumentException(
                "${unitType.description}의 최대 승무원 수(${fleet.maxCrew}명)에 도달했습니다"
            )
        }

        // Check if slot is already filled
        val existingInSlot = unitCrewRepository.findByFleetIdAndSlotRole(fleet.id, slotRole.name)
        if (existingInSlot != null) {
            throw IllegalArgumentException(
                "${slotRole.displayNameKo} 슬롯은 이미 배정되어 있습니다"
            )
        }
    }
}
