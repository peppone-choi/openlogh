package com.openlogh.command.gin7.commander

import com.openlogh.command.CommandCost
import com.openlogh.command.CommandEnv
import com.openlogh.command.CommandResult
import com.openlogh.command.OfficerCommand
import com.openlogh.engine.tactical.ai.MissionObjective
import com.openlogh.entity.Officer
import com.openlogh.model.StatCategory
import kotlin.random.Random

/**
 * Phase 12 D-01/D-19: 작전계획 커맨드 — delegates to [com.openlogh.service.OperationPlanService].
 *
 * Args:
 *   - objective: String — "CONQUEST" | "DEFENSE" | "SWEEP"
 *   - targetStarSystemId: Long — the star system this operation targets (D-03)
 *   - participantFleetIds: List<Long> — fleets assigned to this operation
 *   - scale: Int? — MCP leverage, 1..7 (default 1)
 *   - planName: String? — display name (default "작전계획-{year}-{month}")
 *
 * The command does NOT touch the repository directly — all persistence +
 * atomic D-04 enforcement happens inside OperationPlanService.assignOperation
 * under a single @Transactional boundary (CommandExecutor is NOT @Transactional).
 */
class OperationPlanCommand(
    general: Officer,
    env: CommandEnv,
    arg: Map<String, Any>?,
) : OfficerCommand(general, env, arg) {

    override val actionName: String = "작전계획"

    override fun getCost(): CommandCost = CommandCost()

    override fun getCommandPoolType(): StatCategory = StatCategory.MCP

    override fun getPreReqTurn(): Int = 0

    override fun getPostReqTurn(): Int = 0

    override suspend fun run(rng: Random): CommandResult {
        val services = this.services ?: return CommandResult.fail("CommandServices unavailable")
        val opService = services.operationPlanService
            ?: return CommandResult.fail("OperationPlanService unavailable")

        // --- arg parsing ---
        val objectiveStr = arg?.get("objective") as? String
            ?: return CommandResult.fail("objective 미지정")
        val objective = runCatching { MissionObjective.valueOf(objectiveStr) }
            .getOrNull() ?: return CommandResult.fail("objective 값이 올바르지 않습니다: $objectiveStr")

        val targetStarSystemId = (arg["targetStarSystemId"] as? Number)?.toLong()
            ?: return CommandResult.fail("targetStarSystemId 미지정")

        val fleetIdsRaw = arg["participantFleetIds"] as? List<*>
            ?: return CommandResult.fail("participantFleetIds 미지정")
        val participantFleetIds = fleetIdsRaw.mapNotNull { (it as? Number)?.toLong() }

        val scale = (arg["scale"] as? Number)?.toInt() ?: 1
        val planName = arg["planName"] as? String
            ?: "작전계획-${env.year}-${env.month}"

        // --- delegate to @Transactional service (D-04 atomicity lives here) ---
        val plan = try {
            opService.assignOperation(
                sessionId = general.sessionId,
                factionId = general.factionId,
                name = planName,
                objective = objective,
                targetStarSystemId = targetStarSystemId,
                participantFleetIds = participantFleetIds,
                scale = scale,
                issuedByOfficerId = general.id,
            )
        } catch (e: IllegalArgumentException) {
            return CommandResult.fail(e.message ?: "작전 수립 실패")
        }

        // Sync channel wiring: Plan 12-04 Task 2b adds the direct call to
        // TacticalBattleService.syncOperationToActiveBattles(plan) once the
        // method exists (created by Plan 12-03). Not wired here yet.

        pushLog("${general.name}이(가) '${plan.name}' 작전계획을 수립했다 (${plan.objective.korean}).")
        pushNationalHistoryLog("작전계획 수립: ${plan.name} (${plan.objective.korean} @ 성계 ${plan.targetStarSystemId})")
        return CommandResult.success(logs)
    }
}
