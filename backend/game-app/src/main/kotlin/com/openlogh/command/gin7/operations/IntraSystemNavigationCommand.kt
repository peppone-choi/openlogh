package com.openlogh.command.gin7.operations

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.engine.GridCapacityChecker
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * 성계내항행 — 같은 성계 내 행성으로 이동한다.
 * MCP 커맨드. cpCost=160, waitTime=8, duration=0
 *
 * Phase 24-06 (gin7 manual p30): enforces 300-unit/faction grid capacity.
 */
class IntraSystemNavigationCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "성계내항행"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 8

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val destPlanetId = arg?.get("destPlanetId")?.let {
            when (it) {
                is Number -> it.toLong()
                is String -> it.toLongOrNull()
                else -> null
            }
        } ?: return CommandResult.fail("목표 행성 미지정")

        // Phase 24-06: enforce 300-unit/faction grid capacity (gin7 manual p30).
        val fleetRepo = services?.fleetRepository
        if (fleetRepo != null && !GridCapacityChecker.canEnterGrid(
                fleetRepository = fleetRepo,
                sessionId = env.sessionId,
                factionId = general.factionId,
                destPlanetId = destPlanetId,
                movingFleet = troop,
            )) {
            val available = GridCapacityChecker.availableCapacity(
                fleetRepo, env.sessionId, general.factionId, destPlanetId, troop?.id
            )
            return CommandResult.fail(
                "그리드 진입 불가: 목표 행성에 진영 함선 ${300 - available}유닛 이미 배치 (상한 300)"
            )
        }

        val prevPlanetId = general.planetId
        general.planetId = destPlanetId

        // Update fleet's planetId if fleet is available
        troop?.planetId = destPlanetId

        pushLog("${general.name}이(가) 성계 내 항행을 실시했다.")
        pushLog("행성 #${prevPlanetId} → 행성 #${destPlanetId}")

        return CommandResult(success = true, logs = logs)
    }
}
