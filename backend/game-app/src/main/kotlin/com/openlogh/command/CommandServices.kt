package com.openlogh.command

import com.openlogh.engine.DiplomacyService
import com.openlogh.engine.modifier.ModifierService
import com.openlogh.repository.FleetRepository
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.FactionRepository
import com.openlogh.service.OfficerPoolService
import com.openlogh.service.MessageService
import com.openlogh.service.FactionService
import com.openlogh.service.OperationPlanService

/**
 * 커맨드에서 접근 가능한 서비스/리포지토리 홀더.
 * CommandExecutor가 커맨드 실행 전 주입한다.
 */
data class CommandServices(
    val officerRepository: OfficerRepository,
    val planetRepository: PlanetRepository,
    val factionRepository: FactionRepository,
    val diplomacyService: DiplomacyService,
    val messageService: MessageService? = null,
    val factionService: FactionService? = null,
    val officerPoolService: OfficerPoolService? = null,
    val modifierService: ModifierService? = null,
    val fleetRepository: FleetRepository? = null,
    val operationPlanService: OperationPlanService? = null,
) {
    /**
     * Resolve city name by ID. Returns null if not found.
     */
    suspend fun getCityName(cityId: Long): String? {
        return try {
            planetRepository.findById(cityId).orElse(null)?.name
        } catch (_: Exception) {
            null
        }
    }
}
