package com.openlogh.command.gin7.commander

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 부대해산 커맨드 — MCP 160 소모.
 * Fleet을 삭제하고 소속 장교들의 fleetId를 0으로 초기화한다.
 */
class DisbandFleetCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "부대해산"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val fleetRepo = services?.fleetRepository
            ?: return CommandResult.fail("서비스 미초기화")
        val officerRepo = services?.officerRepository
            ?: return CommandResult.fail("서비스 미초기화")

        val fleetId = general.fleetId
        if (fleetId == 0L) return CommandResult.fail("소속 함대가 없습니다.")

        // 함대 소속 장교들 fleetId 초기화
        officerRepo.findByFleetId(fleetId).forEach { officer ->
            officer.fleetId = 0L
            officerRepo.save(officer)
        }

        fleetRepo.deleteById(fleetId)
        general.fleetId = 0L

        pushLog("부대를 해산했다.")

        return CommandResult.success(logs)
    }
}
