package com.openlogh.command.gin7.logistics

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 반출입 — 행성과 함대 간 물자를 이동한다.
 * MCP 커맨드. cpCost=160, waitTime=0, duration=0
 *
 * arg["direction"]: "IN" (행성→함대) or "OUT" (함대→행성)
 * arg["amount"]: 이동할 물자량 (기본 500)
 */
class TransferGoodsCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "반출입"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val planet = city
            ?: return CommandResult.fail("현재 행성 정보 없음")

        val direction = arg?.get("direction") as? String ?: "IN"
        val amount = arg?.get("amount")?.let {
            when (it) {
                is Number -> it.toInt()
                is String -> it.toIntOrNull()
                else -> null
            }
        } ?: 500

        return when (direction) {
            "IN" -> {
                // Planet production → officer supplies (planet has no supplies field, use production as proxy)
                val actual = minOf(amount, planet.production)
                planet.production = maxOf(0, planet.production - actual)
                general.supplies = minOf(general.supplies + actual, 9999)
                pushLog("${planet.name}에서 ${actual} 물자를 반입했다.")
                CommandResult(success = true, logs = logs)
            }
            "OUT" -> {
                // Officer supplies → planet production
                val actual = minOf(amount, general.supplies)
                general.supplies = maxOf(0, general.supplies - actual)
                planet.production += actual
                pushLog("${planet.name}에 ${actual} 물자를 반출했다.")
                CommandResult(success = true, logs = logs)
            }
            else -> CommandResult.fail("유효하지 않은 방향: $direction (IN/OUT만 허용)")
        }
    }
}
