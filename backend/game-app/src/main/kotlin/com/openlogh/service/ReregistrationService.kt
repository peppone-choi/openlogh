package com.openlogh.service

import com.openlogh.entity.Officer
import com.openlogh.repository.FactionRepository
import com.openlogh.repository.OfficerRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Feature 1.7 - Re-registration Restriction
 *
 * Ejected players cannot re-enter as their original character.
 * They must rejoin the same faction they were ejected from.
 */
@Service
class ReregistrationService(
    private val officerRepository: OfficerRepository,
    private val factionRepository: FactionRepository,
) {
    companion object {
        private val log = LoggerFactory.getLogger(ReregistrationService::class.java)
    }

    /**
     * Returns true if this officer (or character) is allowed to register/re-enter.
     * Ejected players (meta["ejectedFrom"] present) cannot use their original character
     * and must rejoin the same faction.
     */
    fun canReregister(sessionId: Long, userId: Long, targetFactionId: Long, uniqueName: String): ReregistrationResult {
        val existingOfficers = officerRepository.findBySessionIdAndUserId(sessionId, userId)

        // Find any previously ejected record for this user+character
        val ejectedOfficer = existingOfficers.firstOrNull { o ->
            o.meta.containsKey("ejectedFrom") && o.meta["wasOriginal"] == true
        }

        if (ejectedOfficer != null) {
            val ejectedFromFactionId = (ejectedOfficer.meta["ejectedFrom"] as? Number)?.toLong()

            // Must rejoin the same faction
            if (ejectedFromFactionId != null && targetFactionId != ejectedFromFactionId) {
                val faction = factionRepository.findById(ejectedFromFactionId).orElse(null)
                val factionName = faction?.name ?: ejectedFromFactionId.toString()
                return ReregistrationResult(
                    allowed = false,
                    reason = "추방된 플레이어는 원래 진영(${factionName})에만 재가입할 수 있습니다.",
                )
            }

            // Cannot use original character name
            if (ejectedOfficer.personalCode.isNotBlank() && ejectedOfficer.personalCode != "None") {
                return ReregistrationResult(
                    allowed = false,
                    reason = "추방된 플레이어는 원래 캐릭터를 사용할 수 없습니다. 새 캐릭터로 재가입하십시오.",
                )
            }
        }

        return ReregistrationResult(allowed = true)
    }

    /**
     * Mark an officer as ejected. Sets meta flags for re-registration restriction tracking.
     */
    fun markAsEjected(officer: Officer) {
        officer.meta["ejectedFrom"] = officer.factionId
        officer.meta["wasOriginal"] = true
        officer.meta["ejectedAt"] = System.currentTimeMillis()
        officerRepository.save(officer)
        log.info("Officer {} marked as ejected from faction {}", officer.id, officer.factionId)
    }

    data class ReregistrationResult(
        val allowed: Boolean,
        val reason: String? = null,
    )
}
