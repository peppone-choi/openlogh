package com.openlogh.service

import com.openlogh.repository.OfficerRepository
import org.springframework.stereotype.Service

@Service
class PermissionService(
    private val officerRepository: OfficerRepository,
) {
    data class PermissionResult(
        val result: Boolean,
        val reason: String,
    )

    fun setPermission(
        userId: Long,
        nationId: Long,
        isAmbassador: Boolean,
        generalIds: List<Long>,
    ): PermissionResult {
        val targetPermission = if (isAmbassador) "ambassador" else "auditor"
        val maxCount = if (isAmbassador) 2 else Int.MAX_VALUE

        if (generalIds.size > maxCount) {
            return PermissionResult(false, "외교권자는 최대 둘까지만 설정 가능합니다.")
        }

        val leaders = officerRepository.findByUserId(userId)
        val leader = leaders.firstOrNull()
            ?: return PermissionResult(false, "장수를 찾을 수 없습니다.")

        val factionOfficers = officerRepository.findByNationId(nationId)

        // Clear existing officers with the target permission
        factionOfficers.filter { it.permission == targetPermission }.forEach { officer ->
            officer.permission = "normal"
            officerRepository.save(officer)
        }

        // Apply new permissions
        generalIds.forEach { id ->
            val officer = factionOfficers.find { it.id == id } ?: return@forEach
            // Skip if penalty noChief
            if (officer.penalty["noChief"] == true) return@forEach
            // Skip rulers (rank >= 20)
            if (officer.rank >= 20.toShort()) return@forEach

            officer.permission = targetPermission
            officerRepository.save(officer)
        }

        return PermissionResult(true, "success")
    }
}
