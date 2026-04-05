package com.openlogh.service

import com.openlogh.repository.GeneralRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Manages ambassador/auditor permission assignments for a nation.
 * Legacy: j_general_set_permission.php
 */
@Service
@Transactional
class PermissionService(
    private val generalRepository: GeneralRepository,
) {
    data class PermissionResult(val result: Boolean, val reason: String)

    /**
     * Sets ambassador or auditor permissions for specified generals.
     * Only the nation leader (officer_level=20) can do this.
     *
     * @param userId the authenticated user making the request
     * @param nationId the nation being modified
     * @param isAmbassador true for ambassador (외교권자), false for auditor (감찰관)
     * @param generalIds list of general IDs to assign the permission
     */
    @Transactional
    fun setPermission(userId: Long, nationId: Long, isAmbassador: Boolean, generalIds: List<Long>): PermissionResult {
        val requester = generalRepository.findByUserId(userId)
            .firstOrNull { it.nationId == nationId && it.npcState.toInt() == 0 }
            ?: return PermissionResult(false, "장수를 찾을 수 없습니다.")

        if (requester.officerLevel.toInt() != 20) {
            return PermissionResult(false, "군주가 아닙니다")
        }

        val targetType = if (isAmbassador) "ambassador" else "auditor"
        val targetLevel = if (isAmbassador) 4 else 3
        val maxCount = if (isAmbassador) 2 else Int.MAX_VALUE

        if (generalIds.size > maxCount) {
            return PermissionResult(false, "외교권자는 최대 둘까지만 설정 가능합니다.")
        }

        // Clear existing permissions of this type in the nation
        val nationGenerals = generalRepository.findByNationId(nationId)
        nationGenerals.filter { it.permission == targetType }.forEach { g ->
            g.permission = "normal"
            generalRepository.save(g)
        }

        if (generalIds.isEmpty()) {
            return PermissionResult(true, "success")
        }

        // Set new permissions
        val assignableIds = nationGenerals.asSequence()
            .filter { it.id in generalIds }
            .filter { it.officerLevel.toInt() != 20 }
            .filter { it.permission == "normal" }
            .filter { checkSecretMaxPermission(it.penalty) >= targetLevel }
            .map { it.id }
            .toSet()

        for (general in nationGenerals) {
            if (general.id !in assignableIds) {
                continue
            }
            general.permission = targetType
            generalRepository.save(general)
        }

        return PermissionResult(true, "success")
    }

    private fun checkSecretMaxPermission(penalty: Map<String, Any>): Int {
        return when {
            penalty["noTopSecret"] == true -> 1
            penalty["noChief"] == true -> 1
            penalty["noAmbassador"] == true -> 2
            else -> 4
        }
    }
}
