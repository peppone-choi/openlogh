package com.openlogh.command.gin7.logistics

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 완전수리 — 함대의 ShipUnit들을 최대 함선 수로 복구한다.
 * MCP 커맨드. cpCost=160, waitTime=0, duration=0
 */
class FullRepairCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "완전수리"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val fleet = troop
            ?: return CommandResult.fail("소속 함대 없음")

        // Full repair restores fleet to operational status.
        // ShipUnit-level restoration is handled by ShipUnitService in Phase 3.
        // Here we mark the fleet meta so the turn engine can process the restoration.
        fleet.meta["pendingFullRepair"] = true

        pushLog("${general.name}의 함대 완전 수리를 명령했다. (함대 ID: ${fleet.id})")

        return CommandResult(success = true, logs = logs)
    }
}
