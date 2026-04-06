package com.openlogh.command.gin7.personal

import com.openlogh.command.BaseCommand
import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import kotlin.random.Random

/**
 * 원거리이동 커맨드 — PCP 10 소모, Officer.planetId 변경
 */
class LongRangeMoveCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "원거리이동"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val destPlanetId = when (val v = arg?.get("destPlanetId")) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull()
            else -> null
        } ?: return CommandResult.fail("목표 행성 미지정")

        general.planetId = destPlanetId
        pushLog("${general.name}이(가) 원거리 이동했다.")
        return CommandResult.success(logs)
    }
}

/**
 * 근거리이동 커맨드 — PCP 5 소모, Officer.planetId 변경
 */
class ShortRangeMoveCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "근거리이동"

    override fun getCost(): CommandCost = CommandCost()

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val destPlanetId = when (val v = arg?.get("destPlanetId")) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull()
            else -> null
        } ?: return CommandResult.fail("목표 행성 미지정")

        general.planetId = destPlanetId
        pushLog("${general.name}이(가) 근거리 이동했다.")
        return CommandResult.success(logs)
    }
}
