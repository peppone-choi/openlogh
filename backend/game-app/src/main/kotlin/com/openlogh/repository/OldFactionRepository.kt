package com.openlogh.repository

import com.openlogh.entity.OldFaction
import org.springframework.data.jpa.repository.JpaRepository

interface OldFactionRepository : JpaRepository<OldFaction, Long> {
    fun findByServerId(serverId: String): List<OldFaction>
    fun findByServerIdAndFaction(serverId: String, faction: Long): OldFaction?
}
