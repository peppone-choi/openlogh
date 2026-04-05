package com.openlogh.service

import com.openlogh.repository.OfficerRepository
import org.springframework.stereotype.Service

/**
 * Exports officer data for the battle simulator.
 * Legacy: j_export_simulator_object.php
 */
@Service
class SimulatorExportService(
    private val officerRepository: OfficerRepository,
) {
    data class ExportResult(val result: Boolean, val reason: String? = null, val data: Map<String, Any?>? = null)

    fun exportGeneralForSimulator(requesterId: Long, targetId: Long): ExportResult {
        if (targetId <= 0) {
            return ExportResult(false, "올바르지 않은 장수 코드입니다")
        }

        val requester = officerRepository.findById(requesterId).orElse(null)
            ?: return ExportResult(false, "요청자를 찾을 수 없습니다.")

        val target = officerRepository.findById(targetId).orElse(null)
            ?: return ExportResult(false, "대상 장수를 찾을 수 없습니다.")

        // Only same-faction generals can export (or self)
        if (requester.factionId != target.factionId && requester.id != target.id) {
            return ExportResult(false, "같은 국가의 장수만 추출할 수 있습니다.")
        }

        val data = mapOf<String, Any?>(
            "name" to target.name,
            "faction" to target.factionId,
            "officerLevel" to target.officerLevel.toInt(),
            "leadership" to target.leadership.toInt(),
            "strength" to target.command.toInt(),
            "intel" to target.intelligence.toInt(),
            "experience" to target.experience,
            "crew" to target.ships,
            "crewtype" to target.shipClass,
            "train" to target.training.toInt(),
            "atmos" to target.morale.toInt(),
            "weapon" to target.flagshipCode,
            "book" to target.equipCode,
            "horse" to target.engineCode,
            "item" to target.accessoryCode,
            "personal" to target.personalCode,
            "specialWar" to target.special2Code,
            "defenceTrain" to target.defenceTrain.toInt(),
            "rice" to target.supplies,
            "injury" to target.injury.toInt(),
            "dex" to target.meta["dex"],
            "rank" to target.meta["rank"],
        )

        return ExportResult(true, data = data)
    }
}
