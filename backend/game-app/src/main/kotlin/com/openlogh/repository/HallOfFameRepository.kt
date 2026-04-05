package com.openlogh.repository

import com.openlogh.entity.HallOfFame
import org.springframework.data.jpa.repository.JpaRepository

interface HallOfFameRepository : JpaRepository<HallOfFame, Long> {
    fun findByServerId(serverId: String): List<HallOfFame>
    fun findByServerIdAndTypeAndOfficerNo(serverId: String, type: String, officerNo: Long): HallOfFame?
}
