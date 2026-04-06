package com.openlogh.command.gin7.commander

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Fleet
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 부대결성 커맨드 — MCP 320 소모.
 * Fleet 엔티티를 신규 생성하고 general을 leaderOfficerId로 설정한다.
 */
class FormFleetCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "부대결성"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val fleetRepo = services?.fleetRepository
            ?: return CommandResult.fail("서비스 미초기화")

        val fleetName = arg?.get("name") as? String ?: "${general.name}의 함대"
        val unitType = arg?.get("unitType") as? String ?: "FLEET"

        val newFleet = Fleet(
            sessionId = general.sessionId,
            leaderOfficerId = general.id,
            factionId = general.factionId,
            name = fleetName,
            unitType = unitType,
            maxUnits = 60,
            planetId = general.planetId,
        )

        val saved = fleetRepo.save(newFleet)
        general.fleetId = saved.id

        pushLog("${general.name}이(가) '${fleetName}'을(를) 결성했다.")
        pushGlobalHistoryLog("부대 결성: $fleetName")

        return CommandResult.success(logs)
    }
}
