package com.openlogh.command.gin7.personal

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
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
 * 반란 커맨드 — PCP 640 소모, meta에 rebellionStatus = ACTIVE 기록
 */
class RebellionCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "반란"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        general.meta["rebellionStatus"] = "ACTIVE"

        pushGlobalHistoryLog("${general.name}이(가) 반란을 일으켰다!")
        pushLog("${general.name}이(가) 반란을 일으켰다!")
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
