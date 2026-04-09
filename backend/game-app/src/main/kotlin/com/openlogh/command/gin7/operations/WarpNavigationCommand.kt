package com.openlogh.command.gin7.operations

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 워프항행 — 목표 행성으로 워프 이동한다.
 * MCP 커맨드. cpCost=40, waitTime=0, duration=0 (Phase 3에서 거리 연동 예정)
 */
class WarpNavigationCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "워프항행"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val destPlanetId = arg?.get("destPlanetId")?.let {
            when (it) {
                is Number -> it.toLong()
                is String -> it.toLongOrNull()
                else -> null
            }
        } ?: return CommandResult.fail("목표 행성 미지정")

        val prevPlanetId = general.planetId
        general.planetId = destPlanetId

        // Phase 12 fix: Update fleet's planetId alongside officer
        // (mirrors IntraSystemNavigationCommand.kt:44). Without this,
        // OPS-03 (arrival-triggered operation activation) is broken for
        // all cross-system operations because Fleet.planetId stays stale.
        troop?.planetId = destPlanetId

        pushLog("${general.name}이(가) 워프 항행을 명령했다.")
        pushLog("행성 #${prevPlanetId} → 행성 #${destPlanetId}")

        return CommandResult(success = true, logs = logs)
    }
}
