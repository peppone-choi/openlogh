package com.openlogh.command

import com.openlogh.engine.DiplomacyService
import com.openlogh.engine.modifier.ModifierService
import com.openlogh.repository.CityRepository
import com.openlogh.repository.GeneralRepository
import com.openlogh.repository.NationRepository
import com.openlogh.service.GeneralPoolService
import com.openlogh.service.MessageService
import com.openlogh.service.NationService

/**
 * 커맨드에서 접근 가능한 서비스/리포지토리 홀더.
 * CommandExecutor가 커맨드 실행 전 주입한다.
 */
data class CommandServices(
    val generalRepository: GeneralRepository,
    val cityRepository: CityRepository,
    val nationRepository: NationRepository,
    val diplomacyService: DiplomacyService,
    val messageService: MessageService? = null,
    val nationService: NationService? = null,
    val generalPoolService: GeneralPoolService? = null,
    val modifierService: ModifierService? = null,
) {
    /**
     * Resolve city name by ID. Returns null if not found.
     */
    suspend fun getCityName(cityId: Long): String? {
        return try {
            cityRepository.findById(cityId).orElse(null)?.name
        } catch (_: Exception) {
            null
        }
    }
}
