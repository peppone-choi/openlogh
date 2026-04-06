package com.openlogh.command.gin7.logistics

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 할당 — 대상 장교에게 자금을 할당한다.
 * MCP 커맨드. cpCost=160, waitTime=0, duration=0
 */
class AllocateCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "할당"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val targetOfficerId = arg?.get("targetOfficerId")?.let {
            when (it) {
                is Number -> it.toLong()
                is String -> it.toLongOrNull()
                else -> null
            }
        } ?: return CommandResult.fail("대상 장교 미지정")

        val amount = arg["funds"]?.let {
            when (it) {
                is Number -> it.toInt()
                is String -> it.toIntOrNull()
                else -> null
            }
        } ?: return CommandResult.fail("할당 자금 미지정")

        // Deduct from faction funds if available, otherwise from general's personal funds
        val factionFunds = nation?.funds ?: 0
        return if (nation != null && factionFunds >= amount) {
            nation!!.funds -= amount

            // Look up target officer and transfer
            val targetOfficer = services?.officerRepository?.findById(targetOfficerId)?.orElse(null)
            if (targetOfficer != null) {
                targetOfficer.funds += amount
                pushLog("${general.name}이(가) ${targetOfficer.name}에게 ${amount} 자금을 할당했다.")
            } else {
                pushLog("${general.name}이(가) 장교 #${targetOfficerId}에게 ${amount} 자금을 할당했다.")
            }

            CommandResult(success = true, logs = logs)
        } else if (general.funds >= amount) {
            general.funds -= amount

            val targetOfficer = services?.officerRepository?.findById(targetOfficerId)?.orElse(null)
            if (targetOfficer != null) {
                targetOfficer.funds += amount
                pushLog("${general.name}이(가) ${targetOfficer.name}에게 ${amount} 자금을 할당했다.")
            } else {
                pushLog("${general.name}이(가) 장교 #${targetOfficerId}에게 ${amount} 자금을 할당했다.")
            }

            CommandResult(success = true, logs = logs)
        } else {
            CommandResult.fail("자금 부족 (필요: ${amount}, 보유: ${nation?.funds ?: general.funds})")
        }
    }
}
