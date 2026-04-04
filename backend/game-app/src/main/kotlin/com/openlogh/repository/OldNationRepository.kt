package com.openlogh.repository

import com.openlogh.entity.OldNation
import org.springframework.data.jpa.repository.JpaRepository

interface OldNationRepository : JpaRepository<OldNation, Long> {
    fun findByServerId(serverId: String): List<OldNation>
    fun findByServerIdAndNation(serverId: String, nation: Long): OldNation?
}
