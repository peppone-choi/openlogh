package com.openlogh.command.gin7.personal

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import kotlin.random.Random

/**
 * 수강 커맨드 — PCP 160 소모, intelligence +1 (cap 100)
 * 사관학교 위치 제약은 Phase 2-06 제안 시스템에서 추가 예정
 */
class AttendLectureCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "수강"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        general.intelligence = minOf(100, (general.intelligence + 1).toInt()).toShort()

        pushLog("${general.name}이(가) 수강을 완료했다. (정보력 ${general.intelligence})")
        return CommandResult.success(logs)
    }
}

/**
 * 병기연습 커맨드 — PCP 10 소모, attack +1 (cap 100)
 * 사관학교 위치 제약은 Phase 2-06 제안 시스템에서 추가 예정
 */
class WeaponsDrillCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "병기연습"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        general.attack = minOf(100, (general.attack + 1).toInt()).toShort()

        pushLog("${general.name}이(가) 병기연습을 완료했다. (공격력 ${general.attack})")
        return CommandResult.success(logs)
    }
}
