package com.openlogh.service

import com.openlogh.dto.TroopMemberInfo
import com.openlogh.dto.TroopWithMembers
import com.openlogh.entity.Troop
import com.openlogh.repository.GeneralRepository
import com.openlogh.repository.TroopRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class TroopService(
    private val troopRepository: TroopRepository,
    private val generalRepository: GeneralRepository,
) {
    @Transactional(readOnly = true)
    fun listByNation(nationId: Long): List<TroopWithMembers> {
        val troops = troopRepository.findByNationId(nationId)
        return troops.map { troop ->
            val members = generalRepository.findByTroopId(troop.id)
            TroopWithMembers(troop, members.map { TroopMemberInfo(it.id, it.name, it.picture) })
        }
    }

    @Transactional
    fun create(worldId: Long, leaderGeneralId: Long, nationId: Long, name: String): Troop {
        val troop = troopRepository.save(Troop(
            worldId = worldId,
            leaderGeneralId = leaderGeneralId,
            nationId = nationId,
            name = name,
        ))
        generalRepository.findById(leaderGeneralId).ifPresent { gen ->
            gen.troopId = troop.id
            generalRepository.save(gen)
        }
        return troop
    }

    @Transactional
    fun join(troopId: Long, generalId: Long): Boolean {
        val general = generalRepository.findById(generalId).orElse(null) ?: return false
        general.troopId = troopId
        generalRepository.save(general)
        return true
    }

    @Transactional
    fun exit(generalId: Long): Boolean {
        val general = generalRepository.findById(generalId).orElse(null) ?: return false
        general.troopId = 0
        generalRepository.save(general)
        return true
    }

    @Transactional
    fun rename(troopId: Long, name: String): Troop? {
        val troop = troopRepository.findById(troopId).orElse(null) ?: return null
        troop.name = name
        return troopRepository.save(troop)
    }

    @Transactional
    fun disband(troopId: Long): Boolean {
        if (!troopRepository.existsById(troopId)) return false
        val members = generalRepository.findByTroopId(troopId)
        members.forEach { it.troopId = 0; generalRepository.save(it) }
        troopRepository.deleteById(troopId)
        return true
    }
}
