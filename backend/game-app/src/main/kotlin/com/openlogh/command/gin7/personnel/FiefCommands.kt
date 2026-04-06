package com.openlogh.command.gin7.personnel

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import kotlin.random.Random

/** 봉토 관련 커맨드 그룹 (봉토수여 + 봉토직할). */
object FiefCommands

/**
 * 봉토수여 커맨드 — PCP 640 소모, destPlanet.meta["fiefOfficerId"] = destOfficer.id
 */
class GrantFiefCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "봉토수여"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val target = destOfficer ?: return CommandResult.fail("대상 장교 미지정")
        val planet = destPlanet ?: return CommandResult.fail("대상 행성 미지정")

        planet.meta["fiefOfficerId"] = target.id

        pushLog("${planet.name} 행성이 ${target.name}의 봉토로 지정됐다.")
        return CommandResult.success(logs)
    }
}

/**
 * 봉토직할 커맨드 — PCP 640 소모, destPlanet.meta에서 "fiefOfficerId" 제거
 */
class ReclaimFiefCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "봉토직할"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val planet = destPlanet ?: return CommandResult.fail("대상 행성 미지정")

        planet.meta.remove("fiefOfficerId")

        pushLog("${planet.name} 행성이 직할령으로 환수됐다.")
        return CommandResult.success(logs)
    }
}
