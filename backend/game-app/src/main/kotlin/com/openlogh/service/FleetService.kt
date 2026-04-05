package com.openlogh.service

import com.openlogh.dto.TroopMemberInfo
import com.openlogh.dto.TroopWithMembers
import com.openlogh.entity.Fleet
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.FleetRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FleetService(
    private val fleetRepository: FleetRepository,
    private val officerRepository: OfficerRepository,
) {
    fun listByNation(nationId: Long): List<TroopWithMembers> {
        val troops = fleetRepository.findByFactionId(nationId)
        return troops.map { troop ->
            val members = officerRepository.findByFleetId(troop.id)
            TroopWithMembers(troop, members.map { TroopMemberInfo(it.id, it.name, it.picture) })
        }
    }

    @Transactional
    fun create(worldId: Long, leaderGeneralId: Long, nationId: Long, name: String): Fleet {
        val troop = fleetRepository.save(Fleet(
            sessionId = worldId,
            leaderGeneralId = leaderGeneralId,
            factionId = nationId,
            name = name,
        ))
        officerRepository.findById(leaderGeneralId).ifPresent { gen ->
            gen.fleetId = troop.id
            officerRepository.save(gen)
        }
        return troop
    }

    @Transactional
    fun join(troopId: Long, generalId: Long): Boolean {
        val officer = officerRepository.findById(generalId).orElse(null) ?: return false
        officer.fleetId = troopId
        officerRepository.save(officer)
        return true
    }

    @Transactional
    fun exit(generalId: Long): Boolean {
        val officer = officerRepository.findById(generalId).orElse(null) ?: return false
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
        val members = officerRepository.findByFleetId(troopId)
        members.forEach { it.fleetId = 0; officerRepository.save(it) }
        fleetRepository.deleteById(troopId)
        return true
    }
}
