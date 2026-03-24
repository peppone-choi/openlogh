package com.openlogh.service

import com.openlogh.entity.Officer
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.SelectPoolRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Feature 2.10 - Character Deletion
 *
 * Conditions for deletion:
 *   - rank <= 4 (대령 이하)
 *   - locationState == "planet"
 *   - at residential or hotel facility (meta["facilityType"] in ["residence", "hotel"])
 */
@Service
class CharacterDeletionService(
    private val officerRepository: OfficerRepository,
    private val selectPoolRepository: SelectPoolRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(CharacterDeletionService::class.java)
        private val SAFE_FACILITIES = setOf("residence", "hotel", "residential")
        private const val MAX_DELETABLE_RANK = 4
    }

    /**
     * Validate that the officer meets deletion conditions.
     */
    fun canDelete(officer: Officer): DeletionCheckResult {
        if (officer.rank > MAX_DELETABLE_RANK) {
            return DeletionCheckResult(
                allowed = false,
                reason = "계급이 대령(4) 이하인 장교만 삭제할 수 있습니다. 현재 계급: ${officer.rank}",
            )
        }

        if (officer.locationState != "planet") {
            return DeletionCheckResult(
                allowed = false,
                reason = "행성에 있을 때만 캐릭터를 삭제할 수 있습니다.",
            )
        }

        val facilityType = officer.meta["facilityType"] as? String
        if (facilityType == null || facilityType !in SAFE_FACILITIES) {
            return DeletionCheckResult(
                allowed = false,
                reason = "자택 또는 호텔에 있을 때만 캐릭터를 삭제할 수 있습니다.",
            )
        }

        return DeletionCheckResult(allowed = true)
    }

    /**
     * Delete the officer and free their select pool slot.
     */
    @Transactional
    fun deleteCharacter(officer: Officer): Boolean {
        val check = canDelete(officer)
        if (!check.allowed) {
            log.warn("Delete denied for officer {}: {}", officer.id, check.reason)
            return false
        }

        val officerId = officer.id
        val sessionId = officer.sessionId
        val uniqueName = officer.personalCode

        // Free the select pool slot
        if (uniqueName.isNotBlank() && uniqueName != "None") {
            val poolEntry = selectPoolRepository.findBySessionIdAndUniqueName(sessionId, uniqueName)
            if (poolEntry != null) {
                poolEntry.officerId = null
                poolEntry.ownerId = null
                selectPoolRepository.save(poolEntry)
                log.info("Freed select pool slot for uniqueName={} in session={}", uniqueName, sessionId)
            }
        }

        officerRepository.delete(officer)
        log.info("Character {} deleted (session={}, rank={}, uniqueName={})", officerId, sessionId, officer.rank, uniqueName)
        return true
    }

    data class DeletionCheckResult(
        val allowed: Boolean,
        val reason: String? = null,
    )
}
