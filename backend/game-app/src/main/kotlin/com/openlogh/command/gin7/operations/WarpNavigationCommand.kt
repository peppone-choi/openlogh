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
 * Phase 24-06 (gin7 매뉴얼 p30): 300 유닛/진영 그리드 용량 상한 적용.
 * Phase 24-27 (gap E38): 항행불능 그리드 진입 차단. Planet.meta["navigable"] == false
 *   인 행성으로는 워프할 수 없다. 시나리오 세팅이나 지형 장애물 이벤트로 해당 플래그
 *   가 false 가 되는 행성은 통상 함대가 들어갈 수 없고 실패 메시지를 돌려받는다.
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

        // Phase 24-27 (gap E38): 항행불능 그리드 차단. meta["navigable"] == false
        // 로 표시된 행성은 통상 함대의 워프 진입을 막는다. 값이 없으면(default null)
        // 통과로 해석하여 기존 시나리오와 호환.
        val planetRepo = services?.planetRepository
        if (planetRepo != null) {
            val destPlanet = planetRepo.findById(destPlanetId).orElse(null)
            if (destPlanet != null) {
                val navigableFlag = destPlanet.meta["navigable"]
                val navigable = when (navigableFlag) {
                    is Boolean -> navigableFlag
                    is String -> navigableFlag.toBooleanStrictOrNull() ?: true
                    is Number -> navigableFlag.toInt() != 0
                    else -> true
                }
                if (!navigable) {
                    return CommandResult.fail(
                        "항행불능 그리드: 행성 #${destPlanetId}(${destPlanet.name}) 은 " +
                            "함대가 진입할 수 없는 영역입니다."
                    )
                }
            }
        }

        // Phase 24-06: enforce 300-unit/faction grid capacity (gin7 매뉴얼 p30).
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
