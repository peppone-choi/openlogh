package com.openlogh.repository

import com.openlogh.entity.OldOfficer
import org.springframework.data.jpa.repository.JpaRepository

interface OldOfficerRepository : JpaRepository<OldOfficer, Long> {
    fun findByServerIdAndOfficerNo(serverId: String, officerNo: Long): OldOfficer?
}
