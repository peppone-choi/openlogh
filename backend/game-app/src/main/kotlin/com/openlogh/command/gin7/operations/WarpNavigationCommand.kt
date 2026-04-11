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
 * 워프항행 — 목표 행성으로 워프 이동한다.
 * MCP 커맨드. cpCost=40, waitTime=0, duration=0 (Phase 3에서 거리 연동 예정)
 *
 * Phase 24-06 (gin7 manual p30): enforces 300-unit/faction grid capacity.
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
