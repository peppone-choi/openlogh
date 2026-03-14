package com.opensam.service

import com.opensam.entity.SelectPool
import com.opensam.repository.SelectPoolRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class SelectPoolService(
    private val selectPoolRepository: SelectPoolRepository,
) {
    fun listAll(worldId: Long): List<SelectPool> = selectPoolRepository.findByWorldId(worldId)

    fun listAvailable(worldId: Long): List<SelectPool> = selectPoolRepository.findByWorldIdAndGeneralIdIsNull(worldId)

    @Transactional
    fun create(worldId: Long, uniqueName: String, info: Map<String, Any>): SelectPool {
        return selectPoolRepository.save(
            SelectPool(worldId = worldId, uniqueName = uniqueName, info = info.toMutableMap()),
        )
    }

    @Transactional
    fun bulkCreate(worldId: Long, entries: List<Map<String, Any>>): List<SelectPool> {
        return entries.mapIndexed { idx, entry ->
            val name = (entry["uniqueName"] as? String) ?: "pool-${idx + 1}"
            selectPoolRepository.save(
                SelectPool(worldId = worldId, uniqueName = name, info = entry.toMutableMap()),
            )
        }
    }

    @Transactional
    fun update(id: Long, info: Map<String, Any>): SelectPool? {
        val pool = selectPoolRepository.findById(id).orElse(null) ?: return null
        pool.info = info.toMutableMap()
        return selectPoolRepository.save(pool)
    }

    @Transactional
    fun delete(id: Long): Boolean {
        if (!selectPoolRepository.existsById(id)) return false
        selectPoolRepository.deleteById(id)
        return true
    }

    @Transactional
    fun deleteAllByWorld(worldId: Long) {
        selectPoolRepository.deleteByWorldId(worldId)
    }

    @Transactional
    fun reserve(id: Long, userId: Long, minutes: Int = 10): SelectPool? {
        val pool = selectPoolRepository.findById(id).orElse(null) ?: return null
        if (pool.generalId != null) return null
        if (pool.ownerId != null && pool.reservedUntil?.isAfter(OffsetDateTime.now()) == true) return null
        pool.ownerId = userId
        pool.reservedUntil = OffsetDateTime.now().plusMinutes(minutes.toLong())
        return selectPoolRepository.save(pool)
    }

    @Transactional
    fun releaseExpired(worldId: Long) {
        val now = OffsetDateTime.now()
        selectPoolRepository.findByWorldId(worldId)
            .filter { it.ownerId != null && it.generalId == null && (it.reservedUntil?.isBefore(now) == true) }
            .forEach {
                it.ownerId = null
                it.reservedUntil = null
                selectPoolRepository.save(it)
            }
    }
}
