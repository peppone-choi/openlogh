package com.openlogh.engine.war

import com.openlogh.engine.DeterministicRng
import com.openlogh.entity.Planet
import com.openlogh.entity.Officer
import com.openlogh.entity.Message
import com.openlogh.entity.Record
import com.openlogh.entity.SessionState
import com.openlogh.repository.PlanetRepository
import com.openlogh.repository.OfficerRepository
import com.openlogh.repository.MessageRepository
import com.openlogh.repository.RecordRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Checks whether a general moving from [fromCityId] to the general's current cityId
 * is intercepted by an enemy general in 요격 (ambush) or 순찰 (patrol) stance,
 * and if so resolves a field battle via [FieldBattleService].
 *
 * Rules:
 *  - 요격: interceptor.lastTurn["action"] == "요격"
 *          AND road segment (originCityId ↔ interceptionTargetCityId) overlaps
 *          with the moving path (fromCityId ↔ toCityId) in either direction.
 *  - 순찰: interceptor.lastTurn["action"] == "순찰"
 *          AND patrolCityId is either fromCityId or toCityId.
 *
 * Only triggers between generals of different nations.
 *
 * Result:
 *  - Interceptor wins (result.attackerWon = true): move is cancelled by resetting
 *    movingGeneral.planetId back to [fromCityId].
 *  - Mover wins: interceptor is sent back to their origin/patrol city.
 */
@Component
class FieldBattleTrigger(
    private val fieldBattleService: FieldBattleService,
    private val officerRepository: OfficerRepository,
    private val planetRepository: PlanetRepository,
    private val messageRepository: MessageRepository,
    private val recordRepository: RecordRepository,
) {

    private val logger = LoggerFactory.getLogger(FieldBattleTrigger::class.java)

    private val MOVE_ACTIONS = setOf("이동", "출병", "강행")

    /**
     * Called after a move command has been applied (general.planetId is already the destination).
     *
     * @param movingGeneral  General who just moved (cityId = destination).
     * @param actionCode     Command that was just executed.
     * @param fromCityId     City the general was in BEFORE the move.
     * @param allGenerals    All generals in this world for this turn.
     * @param world          Current world state.
     * @return true if a field battle was triggered.
     */
    fun checkAndTrigger(
        movingGeneral: Officer,
        actionCode: String,
        fromCityId: Long,
        allGenerals: List<Officer>,
        world: SessionState,
    ): Boolean {
        if (actionCode !in MOVE_ACTIONS) return false
        val toCityId = movingGeneral.planetId
        if (toCityId == fromCityId) return false

        val interceptor = findInterceptor(movingGeneral, fromCityId, toCityId, allGenerals)
            ?: return false

        logger.info(
            "[FieldBattle] {} (nation={}) intercepted by {} (nation={}) on road {}→{}",
            movingGeneral.name, movingGeneral.factionId,
            interceptor.name, interceptor.factionId,
            fromCityId, toCityId,
        )

        val interceptorCity = planetRepository.findById(interceptor.planetId).orElse(null)
            ?: run {
                logger.warn("[FieldBattle] Interceptor city {} not found — skipping", interceptor.planetId)
                return false
            }

        val interceptorUnit = WarUnitOfficer(
            interceptor,
            nationTech = 0f,
            isAttacker = true,
            cityLevel = interceptorCity.level.toInt(),
        )
        val movingUnit = WarUnitOfficer(
            movingGeneral,
            nationTech = 0f,
            isAttacker = false,
            cityLevel = interceptorCity.level.toInt(),
        )

        val isAmbush = (interceptor.lastTurn["action"] as? String) == "요격"
        val hiddenSeed = (world.config["hiddenSeed"] as? String) ?: "${world.id}"
        val rng = DeterministicRng.create(
            hiddenSeed, "FieldBattle",
            world.currentYear, world.currentMonth,
            interceptor.id, movingGeneral.id,
        )

        val result = fieldBattleService.resolve(
            interceptor = interceptorUnit,
            target = movingUnit,
            city = interceptorCity,
            rng = rng,
            isAmbush = isAmbush,
            year = world.currentYear.toInt(),
            startYear = world.currentYear.toInt(),
        )

        interceptorUnit.applyResults()
        movingUnit.applyResults()

        if (result.attackerWon) {
            movingGeneral.planetId = fromCityId
            logger.info("[FieldBattle] Interceptor {} won — {} retreats to city {}",
                interceptor.name, movingGeneral.name, fromCityId)
        } else {
            val originCityId = getInterceptorOriginCityId(interceptor)
            if (originCityId > 0L) {
                interceptor.planetId = originCityId
            }
            logger.info("[FieldBattle] Mover {} won — interceptor {} retreats to city {}",
                movingGeneral.name, interceptor.name, interceptor.planetId)
        }

        officerRepository.save(movingGeneral)
        officerRepository.save(interceptor)

        persistFieldBattleLogs(interceptor, movingGeneral, result, interceptorCity, world, isAmbush)

        return true
    }

    private fun findInterceptor(
        movingGeneral: Officer,
        fromCityId: Long,
        toCityId: Long,
        allGenerals: List<Officer>,
    ): Officer? {
        return allGenerals.firstOrNull { candidate ->
            if (candidate.id == movingGeneral.id) return@firstOrNull false
            if (candidate.factionId == movingGeneral.factionId) return@firstOrNull false
            if (candidate.factionId == 0L || movingGeneral.factionId == 0L) return@firstOrNull false
            if (candidate.ships <= 0) return@firstOrNull false

            val action = candidate.lastTurn["action"] as? String ?: return@firstOrNull false

            when (action) {
                "요격" -> {
                    val originCityId =
                        (candidate.lastTurn["originCityId"] as? Number)?.toLong() ?: 0L
                    val interceptionTargetCityId =
                        (candidate.lastTurn["interceptionTargetCityId"] as? Number)?.toLong() ?: 0L
                    (originCityId == fromCityId && interceptionTargetCityId == toCityId) ||
                        (originCityId == toCityId && interceptionTargetCityId == fromCityId)
                }
                "순찰" -> {
                    val patrolCityId =
                        (candidate.lastTurn["patrolCityId"] as? Number)?.toLong() ?: 0L
                    patrolCityId == fromCityId || patrolCityId == toCityId
                }
                else -> false
            }
        }
    }

    private fun getInterceptorOriginCityId(interceptor: Officer): Long {
        return when (interceptor.lastTurn["action"] as? String) {
            "요격" -> (interceptor.lastTurn["originCityId"] as? Number)?.toLong() ?: 0L
            "순찰" -> (interceptor.lastTurn["patrolCityId"] as? Number)?.toLong() ?: interceptor.planetId
            else -> interceptor.planetId
        }
    }

    private fun persistFieldBattleLogs(
        interceptor: Officer,
        mover: Officer,
        result: BattleResult,
        city: Planet,
        world: SessionState,
        isAmbush: Boolean,
    ) {
        val worldId = world.id.toLong()
        val year = world.currentYear.toInt()
        val month = world.currentMonth.toInt()
        val battleType = if (isAmbush) "요격" else "순찰"
        val outcome = if (result.attackerWon) "요격 성공" else "요격 실패"
        val summary =
            "${interceptor.name}이(가) ${mover.name}을(를) ${city.name} 인근에서 ${battleType} — $outcome"

        val messages = mutableListOf<Message>()
        messages += Message(
            sessionId = worldId,
            mailboxCode = "battle_result",
            messageType = "log",
            srcId = interceptor.id,
            destId = interceptor.id,
            payload = mutableMapOf("message" to "<S>◆</>$year 년 ${month}월:$summary", "year" to year, "month" to month),
        )
        messages += Message(
            sessionId = worldId,
            mailboxCode = "battle_result",
            messageType = "log",
            srcId = mover.id,
            destId = mover.id,
            payload = mutableMapOf("message" to "<S>◆</>$year 년 ${month}월:$summary", "year" to year, "month" to month),
        )
        messages += Message(
            sessionId = worldId,
            mailboxCode = "world_record",
            messageType = "log",
            srcId = interceptor.id,
            payload = mutableMapOf("message" to "<C>●</>$month 월:$summary", "year" to year, "month" to month),
        )

        messageRepository.saveAll(messages)
        val records = messages.map { msg ->
            Record(
                worldId = msg.sessionId,
                recordType = msg.mailboxCode,
                srcId = msg.srcId,
                destId = msg.destId,
                year = (msg.payload["year"] as? Int) ?: year,
                month = (msg.payload["month"] as? Int) ?: month,
                payload = msg.payload,
            )
        }
        recordRepository.saveAll(records)
    }
}
