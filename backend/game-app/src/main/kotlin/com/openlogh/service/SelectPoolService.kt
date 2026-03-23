package com.openlogh.service

import com.openlogh.entity.SelectPool
import com.openlogh.repository.SelectPoolRepository
import org.springframework.stereotype.Service

@Service
class SelectPoolService(
    private val selectPoolRepository: SelectPoolRepository,
) {
    fun create(sessionId: Long, uniqueName: String, info: Map<String, Any>): SelectPool {
        val pool = SelectPool(
            sessionId = sessionId,
            uniqueName = uniqueName,
            info = info.toMutableMap(),
        )
        return selectPoolRepository.save(pool)
    }

    fun delete(id: Long): Boolean {
        if (!selectPoolRepository.existsById(id)) return false
        selectPoolRepository.deleteById(id)
        return true
    }

    fun update(id: Long, info: Map<String, Any>): SelectPool? {
        val pool = selectPoolRepository.findById(id).orElse(null) ?: return null
        pool.info = info.toMutableMap()
        return selectPoolRepository.save(pool)
    }

    fun reserve(id: Long, officerId: Long): SelectPool? {
        val pool = selectPoolRepository.findById(id).orElse(null) ?: return null
        if (pool.officerId != null) return null
        pool.officerId = officerId
        return selectPoolRepository.save(pool)
    }
}
