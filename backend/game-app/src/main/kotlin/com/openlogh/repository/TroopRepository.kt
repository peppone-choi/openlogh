package com.openlogh.repository

import com.openlogh.entity.Troop
import org.springframework.data.jpa.repository.JpaRepository

interface TroopRepository : JpaRepository<Troop, Long> {
    fun findByWorldId(worldId: Long): List<Troop>
    fun findByNationId(nationId: Long): List<Troop>
}
