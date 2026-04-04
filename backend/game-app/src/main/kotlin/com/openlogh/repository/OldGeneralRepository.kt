package com.openlogh.repository

import com.openlogh.entity.OldGeneral
import org.springframework.data.jpa.repository.JpaRepository

interface OldGeneralRepository : JpaRepository<OldGeneral, Long> {
    fun findByServerIdAndGeneralNo(serverId: String, generalNo: Long): OldGeneral?
}
