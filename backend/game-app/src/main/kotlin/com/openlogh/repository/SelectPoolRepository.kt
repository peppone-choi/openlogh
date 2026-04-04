package com.openlogh.repository

import com.openlogh.entity.SelectPool
import org.springframework.data.jpa.repository.JpaRepository

interface SelectPoolRepository : JpaRepository<SelectPool, Long> {
    fun findByWorldId(worldId: Long): List<SelectPool>
    fun findByWorldIdAndGeneralIdIsNull(worldId: Long): List<SelectPool>
    fun findByWorldIdAndUniqueName(worldId: Long, uniqueName: String): SelectPool?
    fun deleteByWorldId(worldId: Long)
}
