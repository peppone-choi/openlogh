package com.openlogh.command.gin7.personal

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Faction
import com.openlogh.entity.Officer
import kotlin.random.Random

/**
 * 반의 커맨드 — PCP 640 소모 (정치 효과는 Phase 4)
 */
class ObjectionCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "반의"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        pushLog("${general.name}이(가) 반의를 표명했다.")
        return CommandResult.success(logs)
    }
}

/**
 * 모의 커맨드 — PCP 640 소모, meta에 conspiracyTarget 기록
 */
class ConspiracyCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "모의"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val targetOfficerId = arg?.get("targetOfficerId")
        if (targetOfficerId != null) {
            general.meta["conspiracyTarget"] = targetOfficerId
        }
        pushLog("${general.name}이(가) 모의를 진행했다.")
        return CommandResult.success(logs)
    }
}

/**
 * 설득 커맨드 — PCP 640 소모, 대상 장교 설득
 */
class PersuasionCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "설득"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val target = destOfficer ?: return CommandResult.fail("대상 장교 미지정")

        pushLog("${general.name}이(가) ${target.name}을(를) 설득했다.")
        return CommandResult.success(logs)
    }
}

/**
 * 반란 커맨드 (gin7 manual p11/p27, Phase 24-10 E51).
 *
 * PCP 640 소모. 실행 성공 시:
 *   1. 새 반란군 진영(Faction)을 생성한다 (factionType="rebel", chiefOfficer=반란 주동자).
 *   2. 주동자 및 meta["conspiracySupporters"] 에 기록된 지지자들을 새 진영으로 이동시킨다
 *      (officer.factionId = rebel.id).
 *   3. 반란군 진영의 수도는 주동자의 현재 행성으로 설정한다.
 *   4. 주동자의 meta["rebellionStatus"] 를 "EXECUTED" 로 마킹한다.
 *
 * 최소 요건 — 주동자는 CAPTAIN 계급 이상 (gin7: 일반 사관 이상이면 반란 가능).
 * 이 최소치는 RebellionCommand 단독으로는 관대하며, 사전에 반의/모의/설득 을 거쳐야 실질적 성공.
 *
 * Source: docs/03-analysis/gin7-manual-complete-gap.analysis.md §E51
 * Manual: gin7 p27 "반의/모의/설득/반란" 커맨드군, p11 "반란군진영" 승패 판정.
 */
class RebellionCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "반란"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPointCost(): Int = 640

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val officerRepo = services?.officerRepository
        val factionRepo = services?.factionRepository
        if (officerRepo == null || factionRepo == null) {
            return CommandResult.fail("리포지토리 미주입 (내부 오류)")
        }

        val originalFaction = factionRepo.findById(general.factionId).orElse(null)
            ?: return CommandResult.fail("현재 소속 진영을 찾을 수 없다")

        // Prevent double-execution: an officer already in a rebel faction cannot rebel again.
        if (originalFaction.factionType == "rebel") {
            return CommandResult.fail("이미 반란군 진영에 소속되어 있다")
        }
        if (general.meta["rebellionStatus"] == "EXECUTED") {
            return CommandResult.fail("이미 반란을 실행한 상태이다")
        }

        // Collect supporters recorded by previous 모의/설득 commands.
        // `conspiracySupporters` is a List<Long> of officer ids; fall back to empty.
        @Suppress("UNCHECKED_CAST")
        val supporterIds: List<Long> = (general.meta["conspiracySupporters"] as? List<Any>)
            ?.mapNotNull { (it as? Number)?.toLong() }
            ?: emptyList()

        // Build new rebel faction.
        val rebelFaction = Faction(
            sessionId = env.sessionId,
            name = "${originalFaction.name} 반란군",
            abbreviation = "반",
            color = "#FFA500",
            capitalPlanetId = general.planetId,
            funds = 0,
            supplies = 0,
            taxRate = originalFaction.taxRate,
            techLevel = originalFaction.techLevel,
            factionType = "rebel",
            chiefOfficerId = general.id,
        )
        val savedRebel = factionRepo.save(rebelFaction)

        // Move the leader.
        general.factionId = savedRebel.id
        general.meta["rebellionStatus"] = "EXECUTED"

        // Move supporters (filter out any who no longer exist or are cross-faction).
        var movedSupporters = 0
        for (supporterId in supporterIds) {
            if (supporterId == general.id) continue
            val supporter = officerRepo.findById(supporterId).orElse(null) ?: continue
            if (supporter.factionId != originalFaction.id) continue
            supporter.factionId = savedRebel.id
            officerRepo.save(supporter)
            movedSupporters++
        }

        pushGlobalHistoryLog(
            "${general.name}이(가) ${originalFaction.name}에서 반란을 일으켜 반란군 진영을 수립했다! " +
            "(지지자 ${movedSupporters}명 동참)"
        )
        pushLog("${general.name}이(가) 반란군 진영 '${savedRebel.name}'을 수립했다.")
        pushLog("수도: 행성 #${general.planetId}, 참여 장교 ${1 + movedSupporters}명")
        return CommandResult.success(logs)
    }
}

/**
 * 참가 커맨드 — PCP 160 소모, meta에 participatingEvent 기록
 */
class ParticipateCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "참가"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val eventId = arg?.get("eventId")
        if (eventId != null) {
            general.meta["participatingEvent"] = eventId
        }
        pushLog("${general.name}이(가) 이벤트에 참가했다.")
        return CommandResult.success(logs)
    }
}
