package com.openlogh.command.gin7.operations

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 군기유지 — 함대 사기를 유지·회복한다.
 * MCP 커맨드. cpCost=80, waitTime=0, duration=0
 */
class MaintainDisciplineCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "군기유지"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val prev = general.morale
        general.morale = minOf(100, (general.morale + 5).toInt()).toShort()
        pushLog("${general.name}이(가) 군기를 유지했다. 사기 ${prev} → ${general.morale}")
        return CommandResult(success = true, logs = logs)
    }
}

/**
 * 항공훈련 — 스파르타니안 훈련도를 향상시킨다.
 * MCP 커맨드. cpCost=80, waitTime=0, duration=0
 */
class FlightTrainingCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "항공훈련"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val prev = general.training
        general.training = minOf(100, (general.training + 3).toInt()).toShort()
        pushLog("${general.name}이(가) 항공 훈련을 실시했다. 훈련도 ${prev} → ${general.training}")
        return CommandResult(success = true, logs = logs)
    }
}

/**
 * 육전훈련 — 육전대 훈련도를 향상시킨다.
 * MCP 커맨드. cpCost=80, waitTime=0, duration=0
 */
class GroundCombatTrainingCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "육전훈련"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val prev = general.training
        general.training = minOf(100, (general.training + 3).toInt()).toShort()
        pushLog("${general.name}이(가) 육전 훈련을 실시했다. 훈련도 ${prev} → ${general.training}")
        return CommandResult(success = true, logs = logs)
    }
}

/**
 * 공전훈련 — 함대 공중전 훈련도를 향상시킨다.
 * MCP 커맨드. cpCost=80, waitTime=0, duration=0
 */
class SpaceCombatTrainingCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "공전훈련"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val prev = general.training
        general.training = minOf(100, (general.training + 3).toInt()).toShort()
        pushLog("${general.name}이(가) 공전 훈련을 실시했다. 훈련도 ${prev} → ${general.training}")
        return CommandResult(success = true, logs = logs)
    }
}

/**
 * 육전전술훈련 — 육전대 전술 훈련도를 향상시킨다.
 * MCP 커맨드. cpCost=80, waitTime=0, duration=0
 */
class GroundTacticsTrainingCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "육전전술훈련"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val prev = general.training
        general.training = minOf(100, (general.training + 4).toInt()).toShort()
        pushLog("${general.name}이(가) 육전 전술 훈련을 실시했다. 훈련도 ${prev} → ${general.training}")
        return CommandResult(success = true, logs = logs)
    }
}

/**
 * 공전전술훈련 — 함대 공중전 전술 훈련도를 향상시킨다.
 * MCP 커맨드. cpCost=80, waitTime=0, duration=0
 */
class SpaceTacticsTrainingCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "공전전술훈련"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val prev = general.training
        general.training = minOf(100, (general.training + 4).toInt()).toShort()
        pushLog("${general.name}이(가) 공전 전술 훈련을 실시했다. 훈련도 ${prev} → ${general.training}")
        return CommandResult(success = true, logs = logs)
    }
}
