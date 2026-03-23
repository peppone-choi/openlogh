package com.openlogh.repository

import com.openlogh.entity.SelectPool
import org.springframework.data.jpa.repository.JpaRepository

interface SelectPoolRepository : JpaRepository<SelectPool, Long> {
    fun findBySessionId(sessionId: Long): List<SelectPool>
    fun findBySessionIdAndOfficerIdIsNull(sessionId: Long): List<SelectPool>
    fun findBySessionIdAndUniqueName(sessionId: Long, uniqueName: String): SelectPool?
    fun deleteBySessionId(sessionId: Long)
}
